"""Where the rule engine gets its numbers from.

Two sources, one interface. Memory is precise but needs ptrace access; screen
works anywhere but only sees what is drawn. The bot does not care which it is
given, so a config can move from one to the other without rewriting rules.
"""

from __future__ import annotations

import os
from typing import Any, Protocol


class StateSource(Protocol):
    name: str

    def names(self) -> list[str]: ...
    def values(self) -> dict[str, Any]: ...
    def write(self, key: str, value: Any) -> bool: ...
    @property
    def alive(self) -> bool: ...


class MemorySource:
    """Reads named values through resolved pointer chains."""

    name = "memory"

    def __init__(self, pid: int, table, device=None):
        from ..mem.device import LocalDevice
        from ..mem.table import ResolvedTable

        self.device = device or LocalDevice()
        self.mem = self.device.open_memory(pid)
        self.table = table
        self.live = ResolvedTable(table, self.mem)

    def names(self) -> list[str]:
        return list(self.table.entries)

    def values(self) -> dict[str, Any]:
        out = {}
        for key in self.table.entries:
            v = self.live.read(key)
            out[key] = 0 if v is None else v
        return out

    def write(self, key: str, value: Any) -> bool:
        return self.live.write(key, value)

    @property
    def alive(self) -> bool:
        return self.mem.alive

    def close(self) -> None:
        self.mem.close()


class ScreenSource:
    """Reads named values by probing pixels in a captured frame."""

    name = "screen"

    def __init__(self, probes: dict, backend: str = "auto", save_last: str | None = None):
        from ..screen import Screen, load_probes
        from ..screen.shell import auto_shell

        self.screen = Screen(auto_shell(backend))
        self.probes = load_probes(probes)
        self.save_last = save_last
        self.last_frame = None

    def names(self) -> list[str]:
        return list(self.probes)

    def values(self) -> dict[str, Any]:
        # One capture per tick: every probe reads the same instant, so rules
        # never compare a health bar to a target from a different frame.
        frame = self.screen.capture()
        self.last_frame = frame
        if self.save_last:
            os.makedirs(os.path.dirname(self.save_last) or ".", exist_ok=True)
            frame.save_png(self.save_last)
        return {name: p.read(frame) for name, p in self.probes.items()}

    def write(self, key: str, value: Any) -> bool:
        raise SystemExit(
            "ekran kaynagi belege yazamaz - 'write' eylemi yalnizca "
            "source=memory ile kullanilabilir"
        )

    @property
    def alive(self) -> bool:
        return True

    def close(self) -> None:
        pass
