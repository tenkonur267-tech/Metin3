"""Process discovery and /proc/<pid>/maps parsing."""

from __future__ import annotations

import os
import re
from dataclasses import dataclass
from typing import Iterable, Iterator


@dataclass(frozen=True)
class Region:
    start: int
    end: int
    perms: str
    file_offset: int
    path: str

    @property
    def size(self) -> int:
        return self.end - self.start

    @property
    def readable(self) -> bool:
        return self.perms[0] == "r"

    @property
    def writable(self) -> bool:
        return self.perms[1] == "w"

    @property
    def anonymous(self) -> bool:
        return self.path == ""

    def __str__(self) -> str:
        return f"{self.start:012x}-{self.end:012x} {self.perms} {self.path or '[anon]'}"


_MAPS_LINE = re.compile(
    r"^([0-9a-f]+)-([0-9a-f]+) (\S{4}) ([0-9a-f]+) \S+ \d+\s*(.*)$"
)

# Regions that never hold game state but are huge; skipping them keeps scans fast.
SKIP_PATHS = ("/dev/", "/memfd:", "[vvar]", "[vdso]", "[vsyscall]")


def read_maps(pid: int) -> list[Region]:
    regions: list[Region] = []
    with open(f"/proc/{pid}/maps", "r") as fh:
        for line in fh:
            m = _MAPS_LINE.match(line.rstrip("\n"))
            if not m:
                continue
            start, end, perms, off, path = m.groups()
            regions.append(
                Region(int(start, 16), int(end, 16), perms, int(off, 16), path.strip())
            )
    return regions


def scannable_regions(
    regions: Iterable[Region],
    *,
    heap: bool = True,
    stack: bool = True,
    anon: bool = True,
    libs: bool = True,
    other: bool = False,
) -> list[Region]:
    """Filter down to regions worth scanning for mutable game values.

    Game state on Android lives in: the native heap ([anon:libc_malloc] /
    [heap]), Unity/il2cpp managed heaps (anonymous rw- mappings), .bss/.data of
    loaded libraries, and occasionally the thread stacks.
    """
    out: list[Region] = []
    for r in regions:
        if not (r.readable and r.writable):
            continue
        if any(r.path.startswith(p) for p in SKIP_PATHS):
            continue
        p = r.path
        if p.startswith("[heap]") or "libc_malloc" in p or "scudo" in p:
            keep = heap
        elif p.startswith("[stack"):
            keep = stack
        elif p == "" or p.startswith("[anon"):
            keep = anon
        elif p.endswith(".so") or ".so:" in p or p.endswith(".apk"):
            keep = libs
        else:
            keep = other
        if keep:
            out.append(r)
    return out


def modules(regions: Iterable[Region]) -> dict[str, int]:
    """Map module path -> lowest mapped address (its load base)."""
    bases: dict[str, int] = {}
    for r in regions:
        if not r.path or r.path.startswith("["):
            continue
        cur = bases.get(r.path)
        if cur is None or r.start < cur:
            bases[r.path] = r.start
    return bases


def module_base(regions: Iterable[Region], name: str) -> int | None:
    """Load base of a module matched by basename or path suffix."""
    best: int | None = None
    for path, base in modules(regions).items():
        if path.endswith(name) or os.path.basename(path) == name:
            if best is None or base < best:
                best = base
    return best


def module_for_address(regions: Iterable[Region], addr: int) -> tuple[str, int] | None:
    """Return (module path, offset from its load base) containing addr."""
    bases = modules(regions)
    for r in regions:
        if r.start <= addr < r.end and r.path and not r.path.startswith("["):
            return r.path, addr - bases[r.path]
    return None


def iter_pids() -> Iterator[int]:
    for name in os.listdir("/proc"):
        if name.isdigit():
            yield int(name)


def _read(path: str) -> str:
    try:
        with open(path, "rb") as fh:
            return fh.read().decode("utf-8", "replace")
    except OSError:
        return ""


def process_name(pid: int) -> str:
    """Android app processes are named after their package in cmdline."""
    cmd = _read(f"/proc/{pid}/cmdline").split("\x00")[0].strip()
    if cmd:
        return cmd
    return _read(f"/proc/{pid}/comm").strip()


def find_pids(pattern: str) -> list[tuple[int, str]]:
    """All processes whose name contains `pattern` (case-insensitive)."""
    needle = pattern.lower()
    hits = []
    for pid in iter_pids():
        name = process_name(pid)
        if name and needle in name.lower():
            hits.append((pid, name))
    return sorted(hits)


def resolve_pid(target: str) -> int:
    """Accept a numeric pid or a process-name fragment."""
    if target.isdigit():
        return int(target)
    hits = find_pids(target)
    if not hits:
        raise SystemExit(f"'{target}' ile eslesen surec yok. 'm3 ps' ile listeleyin.")
    if len(hits) > 1:
        lines = "\n".join(f"  {p}  {n}" for p, n in hits)
        raise SystemExit(f"'{target}' birden fazla surece uyuyor:\n{lines}")
    return hits[0][0]
