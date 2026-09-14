"""Pointer-map construction and pointer-chain search.

A dynamic address found by scanning is useless on the next launch: the heap
moves. What survives is a *chain* rooted in a module's .data/.bss, which is at
a fixed offset from that module's load base:

    value = [ [ [libfoo.so + 0x1A2B30] + 0x18 ] + 0x40 ] + 0xC

This module finds those chains by reverse breadth-first search over a map of
"every aligned qword in memory that happens to point at mapped memory".
"""

from __future__ import annotations

import bisect
import json
import os
import struct
import time
from array import array
from dataclasses import dataclass, field
from typing import Iterable

from . import proc
from .rw import ProcessMemory

PTR_SIZE = 8


@dataclass
class PointerMap:
    """Every pointer-looking slot in the target, indexed by what it points at."""

    pid: int
    ptr_size: int = PTR_SIZE
    # Parallel arrays sorted by `targets`; sources[i] holds the address the
    # pointer lives at. Two flat arrays beat a dict of lists by a wide margin
    # here: a big game process yields tens of millions of entries.
    targets: array = field(default_factory=lambda: array("Q"))
    sources: array = field(default_factory=lambda: array("Q"))
    # Module load bases at capture time, so chains can be expressed relatively.
    module_bases: dict[str, int] = field(default_factory=dict)
    static_ranges: list[tuple[int, int, str]] = field(default_factory=list)

    def __len__(self) -> int:
        return len(self.targets)

    # -- construction ------------------------------------------------------
    @classmethod
    def build(
        cls,
        mem: ProcessMemory,
        *,
        include_stack: bool = False,
        progress=None,
    ) -> "PointerMap":
        regions = proc.read_maps(mem.pid)
        pm = cls(pid=mem.pid)
        pm.module_bases = proc.modules(regions)

        # Any writable, file-backed mapping is a static root candidate: that
        # is the .data/.bss/GOT of a loaded .so or of the main executable, and
        # its address is fixed relative to that module's load base.
        for r in regions:
            if not (r.writable and r.path) or r.path.startswith("["):
                continue
            if any(r.path.startswith(p) for p in proc.SKIP_PATHS):
                continue
            pm.static_ranges.append((r.start, r.end, r.path))

        # Sorted region bounds let us reject non-pointer garbage cheaply.
        bounds = sorted((r.start, r.end) for r in regions if r.readable)
        starts = [b[0] for b in bounds]
        ends = [b[1] for b in bounds]
        lo, hi = starts[0], ends[-1]

        sources = pm.sources
        targets = pm.targets
        # other=True: writable file-backed mappings that are not .so (the main
        # executable, the APK's own data segments) hold static roots too, and a
        # chain is only useful if its root is one of them.
        scan = proc.scannable_regions(regions, stack=include_stack, other=True)
        total = sum(r.size for r in scan)
        seen = 0
        unpack = struct.Struct("<Q").unpack_from
        for r in scan:
            for off, buf in mem.read_chunks(r.start, r.size):
                base = r.start + off
                limit = len(buf) - 7
                for i in range(0, limit, 8):
                    v = unpack(buf, i)[0]
                    if v < lo or v > hi or v & 3:
                        continue
                    k = bisect.bisect_right(starts, v) - 1
                    if k >= 0 and v < ends[k]:
                        targets.append(v)
                        sources.append(base + i)
                seen += len(buf)
                if progress:
                    progress(seen, total)
        pm._sort()
        return pm

    def _sort(self) -> None:
        order = sorted(range(len(self.targets)), key=self.targets.__getitem__)
        self.targets = array("Q", (self.targets[i] for i in order))
        self.sources = array("Q", (self.sources[i] for i in order))

    # -- queries -----------------------------------------------------------
    def pointers_into(self, target: int, max_offset: int):
        """Yield (source_addr, offset) for pointers landing just below target."""
        lo = bisect.bisect_left(self.targets, max(target - max_offset, 0))
        hi = bisect.bisect_right(self.targets, target)
        for i in range(lo, hi):
            yield self.sources[i], target - self.targets[i]

    def static_info(self, addr: int) -> tuple[str, int] | None:
        for start, end, path in self.static_ranges:
            if start <= addr < end:
                return path, addr - self.module_bases[path]
        return None

    # -- persistence -------------------------------------------------------
    def save(self, path: str) -> None:
        os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
        meta = {
            "pid": self.pid,
            "count": len(self),
            "module_bases": self.module_bases,
            "static_ranges": [[s, e, p] for s, e, p in self.static_ranges],
            "saved_at": time.time(),
        }
        with open(path, "wb") as fh:
            head = json.dumps(meta).encode()
            fh.write(len(head).to_bytes(4, "little"))
            fh.write(head)
            self.targets.tofile(fh)
            self.sources.tofile(fh)

    @classmethod
    def load(cls, path: str) -> "PointerMap":
        with open(path, "rb") as fh:
            n = int.from_bytes(fh.read(4), "little")
            meta = json.loads(fh.read(n))
            pm = cls(pid=meta["pid"])
            pm.module_bases = meta["module_bases"]
            pm.static_ranges = [(s, e, p) for s, e, p in meta["static_ranges"]]
            count = meta["count"]
            pm.targets.fromfile(fh, count)
            pm.sources.fromfile(fh, count)
        return pm


@dataclass
class Chain:
    """A resolvable pointer path rooted at module+offset."""

    module: str
    base_offset: int
    offsets: list[int]

    @property
    def depth(self) -> int:
        return len(self.offsets)

    def __str__(self) -> str:
        offs = " + ".join(f"0x{o:X}" for o in self.offsets)
        return f"[{os.path.basename(self.module)}+0x{self.base_offset:X}] -> {offs}"

    def to_json(self) -> dict:
        return {
            "module": self.module,
            "base_offset": self.base_offset,
            "offsets": self.offsets,
        }

    @classmethod
    def from_json(cls, d: dict) -> "Chain":
        return cls(d["module"], d["base_offset"], list(d["offsets"]))

    def resolve(self, mem: ProcessMemory, regions=None) -> int | None:
        """Walk the chain in the live process; None if any hop is unreadable."""
        regions = regions if regions is not None else proc.read_maps(mem.pid)
        base = proc.module_base(regions, os.path.basename(self.module))
        if base is None:
            return None
        addr = mem.read_ptr(base + self.base_offset)
        if addr is None:
            return None
        for off in self.offsets[:-1]:
            addr = mem.read_ptr(addr + off)
            if addr is None or addr == 0:
                return None
        return addr + self.offsets[-1]


def find_chains(
    pm: PointerMap,
    target: int,
    *,
    max_depth: int = 5,
    max_offset: int = 0x1000,
    max_results: int = 50,
    node_budget: int = 400_000,
) -> list[Chain]:
    """Reverse-BFS from `target` back to a static root.

    Shallow chains are both faster to resolve and more stable across game
    updates, so the search is breadth-first and stops at `max_results`.
    """
    results: list[Chain] = []
    # queue entries: (address we need a pointer to, offsets collected so far)
    queue: list[tuple[int, list[int]]] = [(target, [])]
    visited: set[int] = {target}
    budget = node_budget

    for _depth in range(max_depth):
        if not queue or len(results) >= max_results:
            break
        nxt: list[tuple[int, list[int]]] = []
        for addr, trail in queue:
            for src, off in pm.pointers_into(addr, max_offset):
                budget -= 1
                if budget <= 0:
                    return results
                offsets = [off] + trail
                static = pm.static_info(src)
                if static is not None:
                    results.append(Chain(static[0], static[1], offsets))
                    if len(results) >= max_results:
                        return results
                    continue
                if src in visited:
                    continue
                visited.add(src)
                nxt.append((src, offsets))
        queue = nxt
    return results


def verify_chains(
    mem: ProcessMemory,
    chains: Iterable[Chain],
    expected: int,
) -> list[Chain]:
    """Keep only chains that still resolve to `expected` right now."""
    regions = proc.read_maps(mem.pid)
    return [c for c in chains if c.resolve(mem, regions) == expected]
