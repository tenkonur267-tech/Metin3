"""Cheat-Engine style value scanning: first scan, then successive refinements."""

from __future__ import annotations

import json
import os
import struct
import time
from dataclasses import dataclass, field
from typing import Any, Iterable

from . import proc, values
from .rw import ProcessMemory


@dataclass
class ScanFilters:
    heap: bool = True
    stack: bool = True
    anon: bool = True
    libs: bool = True
    other: bool = False


@dataclass
class Scan:
    """A scan in progress: the pid, the value type, and surviving candidates."""

    pid: int
    process: str
    type_name: str
    align: int = 4
    filters: ScanFilters = field(default_factory=ScanFilters)
    # addr -> value as of the last scan
    candidates: dict[int, Any] = field(default_factory=dict)
    history: list[str] = field(default_factory=list)

    @property
    def vtype(self) -> values.ValueType:
        return values.get(self.type_name)

    # -- scanning ----------------------------------------------------------
    def first(self, mem: ProcessMemory, wanted: Any | None = None) -> int:
        """Initial pass over the whole address space.

        `wanted=None` snapshots every aligned slot instead, which is what you
        need for unknown-value searches ("HP went down, but I don't know the
        number") — the next pass then compares against this snapshot.
        """
        vt = self.vtype
        regions = proc.scannable_regions(
            proc.read_maps(self.pid),
            heap=self.filters.heap,
            stack=self.filters.stack,
            anon=self.filters.anon,
            libs=self.filters.libs,
            other=self.filters.other,
        )
        found: dict[int, Any] = {}
        target = None if wanted is None else vt.pack(wanted)
        for r in regions:
            for off, buf in mem.read_chunks(r.start, r.size):
                base = r.start + off
                if target is not None:
                    # Exact-value search: bytes.find is far faster than
                    # unpacking every slot.
                    pos = buf.find(target)
                    while pos != -1:
                        if (base + pos) % self.align == 0:
                            found[base + pos] = wanted
                        pos = buf.find(target, pos + 1)
                else:
                    for o, v in vt.iter_values(buf, self.align):
                        found[base + o] = v
        self.candidates = found
        self.history.append("first=" + ("unknown" if wanted is None else str(wanted)))
        return len(found)

    def refine(self, mem: ProcessMemory, op: str, arg: Any | None = None) -> int:
        """Keep only candidates still satisfying `op` against their old value.

        ops: eq, ne, gt, lt, changed, unchanged, increased, decreased,
             between (arg = (lo, hi))
        """
        vt = self.vtype
        survivors: dict[int, Any] = {}
        for addr, old in self._read_current(mem):
            new = old[1]
            prev = old[0]
            if _matches(vt, op, prev, new, arg):
                survivors[addr] = new
        self.candidates = survivors
        self.history.append(f"{op}" + (f"={arg}" if arg is not None else ""))
        return len(survivors)

    def refresh(self, mem: ProcessMemory) -> None:
        """Re-read candidate values without dropping any."""
        for addr, (_prev, new) in self._read_current(mem):
            self.candidates[addr] = new

    def _read_current(self, mem: ProcessMemory):
        """Yield (addr, (previous, current)) for each live candidate.

        Addresses are read in sorted, batched order: candidate sets after the
        first refinement are sparse, but still clustered, so batching
        neighbours into one read beats one syscall per address.
        """
        vt = self.vtype
        addrs = sorted(self.candidates)
        i = 0
        n = len(addrs)
        unpack = struct.Struct("<" + vt.fmt).unpack_from
        while i < n:
            start = addrs[i]
            j = i
            # Grow the batch while the span stays under 64 KiB.
            while j + 1 < n and addrs[j + 1] - start < 65536:
                j += 1
            span = addrs[j] - start + vt.size
            buf = mem.try_read(start, span)
            if buf is None:
                for k in range(i, j + 1):
                    a = addrs[k]
                    raw = mem.try_read(a, vt.size)
                    if raw is not None:
                        yield a, (self.candidates[a], unpack(raw)[0])
            else:
                for k in range(i, j + 1):
                    a = addrs[k]
                    yield a, (self.candidates[a], unpack(buf, a - start)[0])
            i = j + 1

    # -- reporting ---------------------------------------------------------
    def describe(self, limit: int = 20) -> list[str]:
        regions = proc.read_maps(self.pid)
        lines = []
        for addr in sorted(self.candidates)[:limit]:
            where = proc.module_for_address(regions, addr)
            tag = f"{os.path.basename(where[0])}+0x{where[1]:x}" if where else _region_tag(regions, addr)
            lines.append(f"0x{addr:012x}  {self.candidates[addr]!r:>18}  {tag}")
        return lines

    # -- persistence -------------------------------------------------------
    def to_json(self) -> dict:
        return {
            "pid": self.pid,
            "process": self.process,
            "type": self.type_name,
            "align": self.align,
            "filters": vars(self.filters),
            "history": self.history,
            "saved_at": time.time(),
            "candidates": {str(a): v for a, v in self.candidates.items()},
        }

    @classmethod
    def from_json(cls, d: dict) -> "Scan":
        s = cls(
            pid=d["pid"],
            process=d.get("process", ""),
            type_name=d["type"],
            align=d.get("align", 4),
            filters=ScanFilters(**d.get("filters", {})),
        )
        s.history = d.get("history", [])
        s.candidates = {int(a): v for a, v in d.get("candidates", {}).items()}
        return s

    def save(self, path: str) -> None:
        os.makedirs(os.path.dirname(path), exist_ok=True)
        tmp = path + ".tmp"
        with open(tmp, "w") as fh:
            json.dump(self.to_json(), fh)
        os.replace(tmp, path)

    @classmethod
    def load(cls, path: str) -> "Scan":
        with open(path) as fh:
            return cls.from_json(json.load(fh))


def _region_tag(regions: Iterable[proc.Region], addr: int) -> str:
    for r in regions:
        if r.start <= addr < r.end:
            return r.path or "[anon]"
    return "?"


def _matches(vt, op: str, prev, new, arg) -> bool:
    eq = values.equal
    if op == "eq":
        return eq(vt, new, arg)
    if op == "ne":
        return not eq(vt, new, arg)
    if op == "gt":
        return new > arg
    if op == "lt":
        return new < arg
    if op == "between":
        lo, hi = arg
        return lo <= new <= hi
    if op == "changed":
        return not eq(vt, new, prev)
    if op == "unchanged":
        return eq(vt, new, prev)
    if op == "increased":
        return new > prev
    if op == "decreased":
        return new < prev
    raise SystemExit(f"bilinmeyen karsilastirma '{op}'")
