"""Running commands on the device, whether locally (root) or over adb."""

from __future__ import annotations

import shutil
import subprocess
from dataclasses import dataclass


@dataclass
class Shell:
    """A way to execute a device command and capture its raw stdout."""

    prefix: tuple[str, ...] = ()
    name: str = "local"

    def run(self, *cmd: str, timeout: float = 30) -> bytes:
        if self.prefix and self.prefix[-1] == "-c":
            full = list(self.prefix) + [" ".join(cmd)]
        else:
            full = list(self.prefix) + list(cmd)
        proc = subprocess.run(full, capture_output=True, timeout=timeout)
        if proc.returncode != 0 and not proc.stdout:
            err = proc.stderr.decode("utf-8", "replace").strip()
            raise RuntimeError(f"{' '.join(cmd)} basarisiz: {err or proc.returncode}")
        return proc.stdout


def auto_shell(prefer: str = "auto") -> Shell:
    """Pick how to reach the device.

    Root is preferred: `adb exec-out` needs a paired wireless-debugging session
    and adds latency to every capture, while `su -c` is right here.
    """
    if prefer in ("su", "auto") and shutil.which("su"):
        try:
            out = subprocess.run(["su", "-c", "id"], capture_output=True, timeout=10)
            if b"uid=0" in out.stdout:
                return Shell(prefix=("su", "-c"), name="su")
        except (OSError, subprocess.SubprocessError):
            pass
    if prefer in ("adb", "auto") and shutil.which("adb"):
        return Shell(prefix=("adb", "exec-out"), name="adb")
    if prefer in ("local", "auto"):
        return Shell(name="local")
    raise RuntimeError(f"'{prefer}' backend'i kullanilamiyor")
