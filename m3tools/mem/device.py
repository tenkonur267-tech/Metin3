"""Choosing where the target process lives.

Everything above this layer works the same whether the game runs on this
machine or on an emulator reached over adb; only how you list processes, read
maps and open memory differs.
"""

from __future__ import annotations

import os

from . import proc
from .rw import ProcessMemory, can_ptrace


class LocalDevice:
    """Processes in this kernel namespace, read through /proc."""

    name = "local"

    def check(self) -> str:
        return f"uid={os.getuid()}"

    def list_processes(self) -> list[tuple[int, str]]:
        return proc.find_pids("")

    def read_maps(self, pid: int):
        return proc.read_maps(pid)

    def alive(self, pid: int) -> bool:
        return os.path.isdir(f"/proc/{pid}")

    def open_memory(self, pid: int) -> ProcessMemory:
        ok, why = can_ptrace(pid)
        if not ok:
            raise SystemExit(f"pid {pid} okunamiyor: {why}")
        return ProcessMemory(pid)


def resolve_pid(device, target: str) -> int:
    """Accept a numeric pid or a process-name fragment, on any device."""
    if target.isdigit():
        return int(target)
    needle = target.lower()
    hits = [(p, n) for p, n in device.list_processes() if needle in n.lower()]
    if not hits:
        raise SystemExit(
            f"'{target}' ile eslesen surec yok. 'm3 ps' ile listeleyin."
        )
    if len(hits) > 1:
        # An app's own process is the one whose name has no ":suffix"; helper
        # processes (:push, :gl) are separate and never hold the game state.
        main = [h for h in hits if ":" not in h[1]]
        if len(main) == 1:
            return main[0][0]
        lines = "\n".join(f"  {p}  {n}" for p, n in hits)
        raise SystemExit(f"'{target}' birden fazla surece uyuyor:\n{lines}")
    return hits[0][0]


def open_device(spec: str, serial: str | None = None, su: bool | None = None):
    """spec: 'local' or 'adb'."""
    if spec == "local":
        return LocalDevice()
    if spec == "adb":
        from .remote import AdbDevice, AdbError

        dev = AdbDevice(serial=serial, su=su)
        try:
            dev.check()
        except AdbError as e:
            raise SystemExit(str(e)) from None
        return dev
    raise SystemExit(f"bilinmeyen cihaz '{spec}' (local veya adb)")
