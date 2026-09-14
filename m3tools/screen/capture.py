"""Grabbing frames off the device."""

from __future__ import annotations

from .frame import Frame
from .shell import Shell, auto_shell


class Screen:
    def __init__(self, shell: Shell | None = None):
        self.shell = shell or auto_shell()

    def capture(self) -> Frame:
        """One raw frame. `screencap` without -p skips PNG encode and decode."""
        return Frame.from_screencap(self.shell.run("screencap", timeout=30))

    def size(self) -> tuple[int, int]:
        out = self.shell.run("wm", "size").decode("utf-8", "replace")
        for line in out.splitlines():
            if "size:" in line:
                part = line.split("size:")[-1].strip()
                w, _, h = part.partition("x")
                if w.strip().isdigit() and h.strip().isdigit():
                    return int(w), int(h)
        frame = self.capture()
        return frame.width, frame.height
