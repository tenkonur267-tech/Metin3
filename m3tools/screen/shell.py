"""Running commands on the device, whether locally (root) or over adb."""

from __future__ import annotations

import os
import shutil
import subprocess
from dataclasses import dataclass, field


@dataclass
class Shell:
    """A way to execute a device command and capture its raw stdout."""

    prefix: tuple[str, ...] = ()
    name: str = "local"
    env: dict[str, str] = field(default_factory=dict)

    def run(self, *cmd: str, timeout: float = 30) -> bytes:
        if self.prefix and self.prefix[-1] == "-c":
            full = list(self.prefix) + [" ".join(cmd)]
        else:
            full = list(self.prefix) + list(cmd)
        environ = {**os.environ, **self.env} if self.env else None
        proc = subprocess.run(full, capture_output=True, timeout=timeout,
                              env=environ)
        if proc.returncode != 0 and not proc.stdout:
            err = proc.stderr.decode("utf-8", "replace").strip()
            raise RuntimeError(f"{' '.join(cmd)} basarisiz: {err or proc.returncode}")
        return proc.stdout


def rish_shell() -> Shell | None:
    """Shizuku's shell, if its helper script is installed and working.

    rish runs commands as uid 2000 (the adb user) without needing an adb
    connection at all, which sidesteps Xiaomi dismissing the pairing dialog the
    moment you switch apps.
    """
    path = shutil.which("rish")
    if not path:
        for guess in (os.path.expanduser("~/rish"), "/data/data/com.termux/files/home/rish"):
            if os.path.isfile(guess):
                path = guess
                break
    if not path:
        return None
    # rish needs the caller's package id to reach the Shizuku service.
    env = {"RISH_APPLICATION_ID": os.environ.get("RISH_APPLICATION_ID", "com.termux")}
    shell = Shell(prefix=(path, "-c"), name="rish", env=env)
    try:
        if b"uid=2000" in shell.run("id", timeout=10):
            return shell
    except (OSError, RuntimeError, subprocess.SubprocessError):
        return None
    return None


def adb_shell() -> Shell | None:
    """adb, but only once a device is actually connected."""
    if not shutil.which("adb"):
        return None
    try:
        out = subprocess.run(["adb", "devices"], capture_output=True, timeout=15)
    except (OSError, subprocess.SubprocessError):
        return None
    lines = out.stdout.decode("utf-8", "replace").splitlines()[1:]
    if not any(line.strip().endswith("\tdevice") or line.strip().endswith(" device")
               for line in lines if line.strip()):
        return None
    return Shell(prefix=("adb", "exec-out"), name="adb")


def auto_shell(prefer: str = "auto") -> Shell:
    """Pick how to reach the device.

    Order reflects both capability and latency: root can do everything and is
    local; rish and adb are equivalent in power (uid 2000) but rish needs no
    connection to stay alive; plain local execution only works for a process
    Termux may already touch.
    """
    if prefer in ("su", "auto") and shutil.which("su"):
        try:
            out = subprocess.run(["su", "-c", "id"], capture_output=True, timeout=10)
            if b"uid=0" in out.stdout:
                return Shell(prefix=("su", "-c"), name="su")
        except (OSError, subprocess.SubprocessError):
            pass
    if prefer in ("rish", "auto"):
        shell = rish_shell()
        if shell is not None:
            return shell
        if prefer == "rish":
            raise RuntimeError(
                "rish bulunamadi veya calismiyor. Shizuku uygulamasindan "
                "'rish' dosyalarini Termux'a kopyalayip "
                "'chmod +x ~/rish' yapin."
            )
    if prefer in ("adb", "auto"):
        shell = adb_shell()
        if shell is not None:
            return shell
        if prefer == "adb":
            raise RuntimeError(
                "adb ile bagli cihaz yok. 'adb devices' ciktisini kontrol edin."
            )
    if prefer in ("local", "auto"):
        return Shell(name="local")
    raise RuntimeError(f"'{prefer}' backend'i kullanilamiyor")
