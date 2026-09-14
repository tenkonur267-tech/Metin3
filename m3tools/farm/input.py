"""Touch/key injection backends.

Termux itself cannot touch another app's window, so every backend here needs
one of:
  * root  - `su -c input tap ...`, or direct writes to /dev/input/eventN
  * adb   - wireless debugging paired from the phone to itself
Direct evdev writes are roughly 50x cheaper than spawning `input`, which
matters when a farm loop taps several times a second.
"""

from __future__ import annotations

import os
import re
import shutil
import struct
import subprocess
import time
from dataclasses import dataclass

# struct input_event { struct timeval time; __u16 type; __u16 code; __s32 value; }
_EVENT_FMT = "llHHi" if struct.calcsize("l") == 8 else "iiHHi"
_EVENT_SIZE = struct.calcsize(_EVENT_FMT)

EV_SYN, EV_KEY, EV_ABS = 0x00, 0x01, 0x03
SYN_REPORT = 0
BTN_TOUCH = 0x14A
ABS_MT_SLOT = 0x2F
ABS_MT_TRACKING_ID = 0x39
ABS_MT_POSITION_X = 0x35
ABS_MT_POSITION_Y = 0x36


class InputBackend:
    name = "base"

    def tap(self, x: int, y: int, duration: float = 0.05) -> None:
        raise NotImplementedError

    def swipe(self, x1: int, y1: int, x2: int, y2: int, duration: float = 0.3) -> None:
        raise NotImplementedError

    def key(self, keycode: int | str) -> None:
        raise NotImplementedError

    def close(self) -> None:
        pass


@dataclass
class ShellInput(InputBackend):
    """Uses Android's own `input` binary. Simple, portable, slow (~150-300ms)."""

    prefix: tuple[str, ...] = ()
    name: str = "shell"

    def _run(self, *cmd: str) -> None:
        full = list(self.prefix) + list(cmd)
        if self.prefix and self.prefix[-1] == "-c":
            full = list(self.prefix) + [" ".join(cmd)]
        subprocess.run(full, check=False, stdout=subprocess.DEVNULL,
                       stderr=subprocess.DEVNULL)

    def tap(self, x: int, y: int, duration: float = 0.05) -> None:
        self._run("input", "tap", str(int(x)), str(int(y)))

    def swipe(self, x1: int, y1: int, x2: int, y2: int, duration: float = 0.3) -> None:
        self._run("input", "swipe", str(int(x1)), str(int(y1)),
                  str(int(x2)), str(int(y2)), str(int(duration * 1000)))

    def key(self, keycode: int | str) -> None:
        self._run("input", "keyevent", str(keycode))


class EvdevInput(InputBackend):
    """Writes multitouch events straight to the touchscreen device node."""

    name = "evdev"

    def __init__(self, device: str | None = None):
        self.path = device or find_touch_device()
        if self.path is None:
            raise RuntimeError("dokunmatik input cihazi bulunamadi")
        self.fd = os.open(self.path, os.O_WRONLY)
        self._tracking = 1

    def _emit(self, events: list[tuple[int, int, int]]) -> None:
        now = time.time()
        sec, usec = int(now), int((now % 1) * 1_000_000)
        blob = b"".join(
            struct.pack(_EVENT_FMT, sec, usec, t, c, v) for t, c, v in events
        )
        os.write(self.fd, blob)

    def _down(self, x: int, y: int) -> None:
        self._tracking = (self._tracking + 1) & 0xFFFF or 1
        self._emit([
            (EV_ABS, ABS_MT_SLOT, 0),
            (EV_ABS, ABS_MT_TRACKING_ID, self._tracking),
            (EV_ABS, ABS_MT_POSITION_X, int(x)),
            (EV_ABS, ABS_MT_POSITION_Y, int(y)),
            (EV_KEY, BTN_TOUCH, 1),
            (EV_SYN, SYN_REPORT, 0),
        ])

    def _move(self, x: int, y: int) -> None:
        self._emit([
            (EV_ABS, ABS_MT_SLOT, 0),
            (EV_ABS, ABS_MT_POSITION_X, int(x)),
            (EV_ABS, ABS_MT_POSITION_Y, int(y)),
            (EV_SYN, SYN_REPORT, 0),
        ])

    def _up(self) -> None:
        self._emit([
            (EV_ABS, ABS_MT_SLOT, 0),
            (EV_ABS, ABS_MT_TRACKING_ID, -1),
            (EV_KEY, BTN_TOUCH, 0),
            (EV_SYN, SYN_REPORT, 0),
        ])

    def tap(self, x: int, y: int, duration: float = 0.05) -> None:
        self._down(x, y)
        time.sleep(duration)
        self._up()

    def swipe(self, x1: int, y1: int, x2: int, y2: int, duration: float = 0.3) -> None:
        steps = max(int(duration / 0.016), 2)
        self._down(x1, y1)
        for i in range(1, steps + 1):
            t = i / steps
            self._move(x1 + (x2 - x1) * t, y1 + (y2 - y1) * t)
            time.sleep(duration / steps)
        self._up()

    def key(self, keycode: int | str) -> None:
        code = int(keycode)
        self._emit([(EV_KEY, code, 1), (EV_SYN, SYN_REPORT, 0)])
        self._emit([(EV_KEY, code, 0), (EV_SYN, SYN_REPORT, 0)])

    def close(self) -> None:
        try:
            os.close(self.fd)
        except OSError:
            pass


def find_touch_device() -> str | None:
    """Pick the evdev node that reports ABS_MT_POSITION_X."""
    try:
        with open("/proc/bus/input/devices") as fh:
            blocks = fh.read().split("\n\n")
    except OSError:
        return None
    best = None
    for block in blocks:
        handlers = re.search(r"^H: Handlers=(.*)$", block, re.M)
        bits = re.search(r"^B: ABS=([0-9a-f ]+)$", block, re.M)
        if not handlers or not bits:
            continue
        m = re.search(r"\b(event\d+)\b", handlers.group(1))
        if not m:
            continue
        # ABS_MT_POSITION_X is bit 0x35; the bitmap is printed as space-
        # separated 64-bit words, most significant first.
        words = bits.group(1).split()
        try:
            value = int("".join(w.zfill(16) for w in words), 16)
        except ValueError:
            continue
        if value >> ABS_MT_POSITION_X & 1:
            path = f"/dev/input/{m.group(1)}"
            if os.access(path, os.W_OK):
                return path
            best = best or path
    return best


def auto_backend(prefer: str = "auto") -> InputBackend:
    """Pick the best available backend for this device."""
    if prefer in ("evdev", "auto"):
        try:
            return EvdevInput()
        except (OSError, RuntimeError):
            if prefer == "evdev":
                raise
    if prefer in ("su", "auto") and shutil.which("su"):
        return ShellInput(prefix=("su", "-c"), name="su")
    if prefer in ("adb", "auto") and shutil.which("adb"):
        return ShellInput(prefix=("adb", "shell"), name="adb")
    if prefer == "shell":
        return ShellInput()
    raise RuntimeError(
        "kullanilabilir girdi backend'i yok. Root (su), adb veya yazilabilir "
        "/dev/input/eventN gerekiyor."
    )
