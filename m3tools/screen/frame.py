"""A screen frame and the pixel maths the bot reasons about.

`adb exec-out screencap` (without -p) writes a tiny header followed by raw
pixels, which is far cheaper to work with than PNG: no decoder, no dependency.
Header layout, little-endian u32 each:

    width, height, format [, colorspace]

The colorspace field was added in Android 9, so its presence is inferred from
the payload length rather than assumed.
"""

from __future__ import annotations

import struct
import zlib
from dataclasses import dataclass

# Values from Android's PixelFormat / HardwareBuffer.
FORMAT_NAMES = {1: "RGBA_8888", 2: "RGBX_8888", 3: "RGB_888", 4: "RGB_565"}
BYTES_PER_PIXEL = {1: 4, 2: 4, 3: 3, 4: 2}


class FrameError(ValueError):
    pass


@dataclass
class Frame:
    width: int
    height: int
    fmt: int
    pixels: bytes  # row-major, BYTES_PER_PIXEL[fmt] per pixel

    @property
    def bpp(self) -> int:
        return BYTES_PER_PIXEL.get(self.fmt, 4)

    @classmethod
    def from_screencap(cls, blob: bytes) -> "Frame":
        if len(blob) < 12:
            raise FrameError(f"screencap ciktisi cok kisa ({len(blob)} bayt)")
        width, height, fmt = struct.unpack_from("<III", blob, 0)
        if not (0 < width <= 16384 and 0 < height <= 16384):
            raise FrameError(f"mantiksiz cozunurluk {width}x{height} - "
                             "screencap ciktisi bozuk olabilir")
        bpp = BYTES_PER_PIXEL.get(fmt)
        if bpp is None:
            raise FrameError(f"bilinmeyen piksel bicimi {fmt}")
        expected = width * height * bpp
        for header in (12, 16):  # Android 9+ araya colorspace ekler
            if len(blob) - header >= expected:
                return cls(width, height, fmt, blob[header:header + expected])
        raise FrameError(
            f"piksel verisi eksik: {len(blob) - 12} bayt var, {expected} bekleniyor"
        )

    # -- pixel access ------------------------------------------------------
    def rgb(self, x: int, y: int) -> tuple[int, int, int]:
        if not (0 <= x < self.width and 0 <= y < self.height):
            raise FrameError(f"({x},{y}) ekran disinda {self.width}x{self.height}")
        bpp = self.bpp
        i = (y * self.width + x) * bpp
        p = self.pixels
        if bpp == 4:
            return p[i], p[i + 1], p[i + 2]
        if bpp == 3:
            return p[i], p[i + 1], p[i + 2]
        # RGB_565
        v = p[i] | (p[i + 1] << 8)
        return ((v >> 11) * 255 // 31, ((v >> 5) & 0x3F) * 255 // 63,
                (v & 0x1F) * 255 // 31)

    def row_rgb(self, y: int, x0: int, x1: int):
        for x in range(x0, x1):
            yield x, self.rgb(x, y)

    # -- export ------------------------------------------------------------
    def to_png(self) -> bytes:
        """Encode as PNG using only zlib, so the user can eyeball a capture."""
        raw = bytearray()
        bpp = self.bpp
        for y in range(self.height):
            raw.append(0)  # filter type 0
            if bpp == 4:
                start = y * self.width * 4
                row = self.pixels[start:start + self.width * 4]
                # Drop alpha: viewers show the UI more predictably without it.
                for i in range(0, len(row), 4):
                    raw += row[i:i + 3]
            else:
                for x in range(self.width):
                    raw += bytes(self.rgb(x, y))

        def chunk(tag: bytes, data: bytes) -> bytes:
            return (len(data).to_bytes(4, "big") + tag + data
                    + zlib.crc32(tag + data).to_bytes(4, "big"))

        header = struct.pack(">IIBBBBB", self.width, self.height, 8, 2, 0, 0, 0)
        return (b"\x89PNG\r\n\x1a\n"
                + chunk(b"IHDR", header)
                + chunk(b"IDAT", zlib.compress(bytes(raw), 6))
                + chunk(b"IEND", b""))

    def save_png(self, path: str) -> None:
        with open(path, "wb") as fh:
            fh.write(self.to_png())

    def __str__(self) -> str:
        name = FORMAT_NAMES.get(self.fmt, str(self.fmt))
        return f"{self.width}x{self.height} {name}"
