"""The offset table: named, resolvable pointers the bot consumes.

This is the artefact the whole scanning workflow exists to produce. It survives
game restarts because every entry is module-relative.
"""

from __future__ import annotations

import json
import os
from dataclasses import dataclass, field

from . import proc
from .pointers import Chain
from .rw import ProcessMemory


@dataclass
class Entry:
    name: str
    type: str
    chain: Chain
    note: str = ""

    def to_json(self) -> dict:
        return {
            "name": self.name,
            "type": self.type,
            "note": self.note,
            "chain": self.chain.to_json(),
        }

    @classmethod
    def from_json(cls, d: dict) -> "Entry":
        return cls(d["name"], d["type"], Chain.from_json(d["chain"]), d.get("note", ""))


@dataclass
class OffsetTable:
    process: str = ""
    entries: dict[str, Entry] = field(default_factory=dict)

    def put(self, entry: Entry) -> None:
        self.entries[entry.name] = entry

    def save(self, path: str) -> None:
        os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
        data = {
            "process": self.process,
            "entries": [e.to_json() for e in self.entries.values()],
        }
        tmp = path + ".tmp"
        with open(tmp, "w") as fh:
            json.dump(data, fh, indent=2)
        os.replace(tmp, path)

    @classmethod
    def load(cls, path: str) -> "OffsetTable":
        if not os.path.exists(path):
            return cls()
        with open(path) as fh:
            d = json.load(fh)
        t = cls(process=d.get("process", ""))
        for e in d.get("entries", []):
            t.put(Entry.from_json(e))
        return t


class ResolvedTable:
    """Live view of an offset table against a running process."""

    def __init__(self, table: OffsetTable, mem: ProcessMemory):
        self.table = table
        self.mem = mem
        self._regions = mem.read_maps()
        self._addrs: dict[str, int] = {}

    def refresh_layout(self) -> None:
        """Re-read maps; needed after the game loads or unloads a library."""
        self._regions = self.mem.read_maps()
        self._addrs.clear()

    def address(self, name: str, cache: bool = True) -> int | None:
        if cache and name in self._addrs:
            return self._addrs[name]
        entry = self.table.entries.get(name)
        if entry is None:
            return None
        addr = entry.chain.resolve(self.mem, self._regions)
        if addr is not None and cache:
            self._addrs[name] = addr
        return addr

    def read(self, name: str, cache_address: bool = True):
        from . import values

        entry = self.table.entries.get(name)
        if entry is None:
            return None
        addr = self.address(name, cache=cache_address)
        if addr is None:
            return None
        vt = values.get(entry.type)
        raw = self.mem.try_read(addr, vt.size)
        if raw is None:
            # A cached address can go stale when the object is freed; drop it
            # so the next call re-walks the chain.
            self._addrs.pop(name, None)
            return None
        return vt.unpack(raw)

    def write(self, name: str, value) -> bool:
        from . import values

        entry = self.table.entries.get(name)
        if entry is None:
            return False
        addr = self.address(name)
        if addr is None:
            return False
        try:
            self.mem.write(addr, values.get(entry.type).pack(value))
            return True
        except Exception:
            self._addrs.pop(name, None)
            return False
