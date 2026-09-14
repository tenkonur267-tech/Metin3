"""Tests for screencap decoding and PNG export."""

from __future__ import annotations

import os
import struct
import sys
import unittest
import zlib

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from m3tools.screen.frame import Frame, FrameError


def screencap_blob(w: int, h: int, fmt: int = 1, colorspace: bool = False,
                   fill: bytes = b"\x10\x20\x30\xff") -> bytes:
    head = struct.pack("<III", w, h, fmt)
    if colorspace:
        head += struct.pack("<I", 0)
    bpp = {1: 4, 2: 4, 3: 3, 4: 2}[fmt]
    return head + (fill * (w * h))[: w * h * bpp]


class TestFrame(unittest.TestCase):
    def test_parses_legacy_12_byte_header(self):
        f = Frame.from_screencap(screencap_blob(4, 3))
        self.assertEqual((f.width, f.height), (4, 3))
        self.assertEqual(f.rgb(0, 0), (0x10, 0x20, 0x30))

    def test_parses_android9_colorspace_header(self):
        f = Frame.from_screencap(screencap_blob(4, 3, colorspace=True))
        self.assertEqual((f.width, f.height), (4, 3))
        self.assertEqual(f.rgb(3, 2), (0x10, 0x20, 0x30))

    def test_rgb565_is_expanded(self):
        # 0xF800 little-endian = pure red in RGB_565.
        blob = struct.pack("<III", 1, 1, 4) + b"\x00\xf8"
        f = Frame.from_screencap(blob)
        self.assertEqual(f.rgb(0, 0), (255, 0, 0))

    def test_truncated_payload_is_rejected(self):
        blob = screencap_blob(10, 10)[:-40]
        with self.assertRaises(FrameError):
            Frame.from_screencap(blob)

    def test_nonsense_header_is_rejected(self):
        with self.assertRaises(FrameError):
            Frame.from_screencap(struct.pack("<III", 0, 0, 1) + b"\x00" * 64)
        with self.assertRaises(FrameError):
            Frame.from_screencap(b"\x00\x01")

    def test_unknown_format_is_rejected(self):
        with self.assertRaises(FrameError):
            Frame.from_screencap(struct.pack("<III", 2, 2, 99) + b"\x00" * 64)

    def test_out_of_bounds_pixel_is_rejected(self):
        f = Frame.from_screencap(screencap_blob(2, 2))
        with self.assertRaises(FrameError):
            f.rgb(2, 0)
        with self.assertRaises(FrameError):
            f.rgb(0, -1)

    def test_png_is_well_formed_and_round_trips(self):
        w, h = 5, 4
        f = Frame.from_screencap(screencap_blob(w, h))
        png = f.to_png()
        self.assertTrue(png.startswith(b"\x89PNG\r\n\x1a\n"))

        # Walk the chunks and verify every CRC.
        pos, chunks, idat = 8, [], b""
        while pos < len(png):
            size = int.from_bytes(png[pos:pos + 4], "big")
            tag = png[pos + 4:pos + 8]
            data = png[pos + 8:pos + 8 + size]
            crc = int.from_bytes(png[pos + 8 + size:pos + 12 + size], "big")
            self.assertEqual(crc, zlib.crc32(tag + data), tag)
            chunks.append(tag)
            if tag == b"IDAT":
                idat += data
            pos += 12 + size
        self.assertEqual(chunks, [b"IHDR", b"IDAT", b"IEND"])

        raw = zlib.decompress(idat)
        self.assertEqual(len(raw), h * (1 + w * 3))
        # Each scanline: filter byte 0 then RGB triples, alpha dropped.
        for y in range(h):
            row = raw[y * (1 + w * 3):(y + 1) * (1 + w * 3)]
            self.assertEqual(row[0], 0)
            self.assertEqual(row[1:4], b"\x10\x20\x30")


if __name__ == "__main__":
    unittest.main()
