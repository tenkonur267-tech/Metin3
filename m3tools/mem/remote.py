"""Reaching a rooted device (or emulator) over adb.

On Windows there is no way to read an emulator's guest memory from the host
directly, so the tools talk to it through adb instead. Every read becomes
`dd` on the device with its output streamed back; `adb exec-out` is used
rather than `adb shell` because only exec-out leaves binary data untouched.

This is slower than reading /proc locally - a process spawn and a round trip
per read instead of a syscall - so the batching knobs are tuned much higher
here, and bulk sweeps are done in large sequential chunks.
"""

from __future__ import annotations

import base64
import shutil
import subprocess

from .base import MemoryHandle
from .proc import Region, _MAPS_LINE

PAGE = 4096


class AdbError(RuntimeError):
    pass


class AdbDevice:
    """A device reachable over adb, with root available through `su`."""

    name = "adb"

    def __init__(self, serial: str | None = None, su: bool = True,
                 adb: str = "adb", timeout: float = 120):
        if shutil.which(adb) is None:
            raise AdbError(
                f"'{adb}' bulunamadi. Android platform-tools kurup PATH'e ekleyin."
            )
        self.adb = adb
        self.serial = serial
        self.su = su
        self.timeout = timeout

    # -- plumbing ----------------------------------------------------------
    def _argv(self, mode: str, command: str) -> list[str]:
        argv = [self.adb]
        if self.serial:
            argv += ["-s", self.serial]
        argv.append(mode)
        if self.su:
            argv += ["su", "-c", command]
        else:
            argv += ["sh", "-c", command]
        return argv

    def exec_out(self, command: str, timeout: float | None = None) -> bytes:
        """Run a device command, returning raw stdout (binary safe)."""
        proc = subprocess.run(
            self._argv("exec-out", command),
            capture_output=True,
            timeout=timeout or self.timeout,
        )
        if proc.returncode != 0 and not proc.stdout:
            err = proc.stderr.decode("utf-8", "replace").strip()
            raise AdbError(err or f"adb cikis kodu {proc.returncode}")
        return proc.stdout

    def shell(self, command: str, timeout: float | None = None) -> str:
        return self.exec_out(command, timeout).decode("utf-8", "replace")

    # -- checks ------------------------------------------------------------
    def devices(self) -> list[tuple[str, str]]:
        argv = [self.adb, "devices"]
        out = subprocess.run(argv, capture_output=True, timeout=30)
        rows = []
        for line in out.stdout.decode("utf-8", "replace").splitlines()[1:]:
            parts = line.split()
            if len(parts) >= 2:
                rows.append((parts[0], parts[1]))
        return rows

    def check(self) -> str:
        """Raise with an actionable message unless the device is usable."""
        rows = self.devices()
        if not rows:
            raise AdbError(
                "adb'ye bagli cihaz yok. Emulator acik mi? "
                "'adb connect 127.0.0.1:5555' deneyin."
            )
        online = [s for s, state in rows if state == "device"]
        if not online:
            states = ", ".join(f"{s}={st}" for s, st in rows)
            raise AdbError(f"cihaz hazir degil: {states}")
        if self.serial is None and len(online) > 1:
            raise AdbError(
                "birden fazla cihaz bagli, --serial ile secin: " + ", ".join(online)
            )
        who = self.shell("id").strip()
        if "uid=0" not in who:
            raise AdbError(
                f"root alinamadi (id: {who or 'bos'}). Emulator ayarlarindan "
                "root iznini acip yeniden baslatin."
            )
        return who

    # -- process info ------------------------------------------------------
    def list_processes(self) -> list[tuple[int, str]]:
        return parse_ps(self.shell("ps -A -o PID,ARGS 2>/dev/null || ps"))

    def read_maps(self, pid: int) -> list[Region]:
        return parse_maps(self.shell(f"cat /proc/{pid}/maps"))

    def alive(self, pid: int) -> bool:
        return bool(self.shell(f"test -d /proc/{pid} && echo y").strip())

    def open_memory(self, pid: int) -> "RemoteProcessMemory":
        return RemoteProcessMemory(self, pid)


def parse_ps(text: str) -> list[tuple[int, str]]:
    """Parse `ps -A -o PID,ARGS` output; tolerant of the plain `ps` fallback."""
    out: list[tuple[int, str]] = []
    for line in text.splitlines():
        parts = line.split(None, 1)
        if len(parts) != 2 or not parts[0].isdigit():
            continue
        name = parts[1].strip()
        if name.upper().startswith(("ARGS", "NAME", "CMD")):
            continue
        # The plain `ps` fallback puts several columns before the name.
        if " " in name and name.split()[0].isdigit():
            name = name.rsplit(None, 1)[-1]
        out.append((int(parts[0]), name))
    return sorted(out)


def parse_maps(text: str) -> list[Region]:
    regions: list[Region] = []
    for line in text.splitlines():
        m = _MAPS_LINE.match(line.rstrip())
        if not m:
            continue
        start, end, perms, off, path = m.groups()
        regions.append(
            Region(int(start, 16), int(end, 16), perms, int(off, 16), path.strip())
        )
    return regions


class RemoteProcessMemory(MemoryHandle):
    """A process's address space, read through adb."""

    # A remote read costs a round trip, so it pays to fetch much more at a
    # time than a local one would.
    batch_span = 4 << 20
    chunk_size = 8 << 20

    def __init__(self, device: AdbDevice, pid: int):
        self.device = device
        self.pid = pid

    @property
    def alive(self) -> bool:
        return self.device.alive(self.pid)

    def read_maps(self) -> list[Region]:
        return self.device.read_maps(self.pid)

    def read(self, addr: int, size: int) -> bytes:
        if size <= 0:
            return b""
        # dd can only skip in whole blocks portably, so read page-aligned and
        # trim - toybox's iflag=skip_bytes is not present on every build.
        start = addr & ~(PAGE - 1)
        lead = addr - start
        pages = (lead + size + PAGE - 1) // PAGE
        cmd = (f"dd if=/proc/{self.pid}/mem bs={PAGE} skip={start // PAGE} "
               f"count={pages} 2>/dev/null")
        raw = self.device.exec_out(cmd)
        if len(raw) < lead + size:
            raise AdbError(
                f"0x{addr:x} kismi okuma ({max(len(raw) - lead, 0)}/{size})"
            )
        return raw[lead:lead + size]

    def write(self, addr: int, data: bytes) -> None:
        if not data:
            return
        payload = base64.b64encode(data).decode("ascii")
        # base64 keeps the bytes intact through the shell; dd with bs=1 seeks
        # by byte, which is what an arbitrary address needs.
        cmd = (f"echo {payload} | base64 -d | "
               f"dd of=/proc/{self.pid}/mem bs=1 seek={addr} conv=notrunc 2>/dev/null")
        self.device.exec_out(cmd)
        check = self.read(addr, len(data))
        if check != data:
            raise AdbError(f"0x{addr:x} yazma dogrulanamadi")
