"""Unit tests for the pieces that do not need a live process."""

from __future__ import annotations

import io
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from m3tools.farm import expr
from m3tools.mem import proc, values
from m3tools.mem.pointers import Chain
from m3tools.mem.scanner import Scan, ScanFilters
from m3tools.mem.table import Entry, OffsetTable

MAPS_SAMPLE = """\
5584d7a00000-5584d7a01000 r--p 00000000 fe:01 1234   /data/app/game/lib/arm64/libil2cpp.so
5584d7a01000-5584d7b00000 r-xp 00001000 fe:01 1234   /data/app/game/lib/arm64/libil2cpp.so
5584d7b00000-5584d7b40000 rw-p 00100000 fe:01 1234   /data/app/game/lib/arm64/libil2cpp.so
7f1000000000-7f1000100000 rw-p 00000000 00:00 0      [anon:libc_malloc]
7f1100000000-7f1100021000 rw-p 00000000 00:00 0      [stack:12345]
7f1200000000-7f1200001000 r--p 00000000 00:00 0      [vvar]
7f1300000000-7f1300010000 rw-s 00000000 00:05 99     /dev/ashmem/whatever
"""


class TestMaps(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.NamedTemporaryFile("w", suffix=".maps", delete=False)
        self.tmp.write(MAPS_SAMPLE)
        self.tmp.close()
        self.addCleanup(os.unlink, self.tmp.name)
        # read_maps opens /proc/<pid>/maps; parse the sample through the same
        # regex by pointing it at a fake pid directory.
        self.regions = self._parse()

    def _parse(self):
        import re
        from m3tools.mem.proc import Region, _MAPS_LINE

        out = []
        for line in MAPS_SAMPLE.splitlines():
            m = _MAPS_LINE.match(line)
            self.assertIsNotNone(m, line)
            a, b, perms, off, path = m.groups()
            out.append(Region(int(a, 16), int(b, 16), perms, int(off, 16), path.strip()))
        return out

    def test_module_base_is_the_lowest_mapping(self):
        self.assertEqual(proc.module_base(self.regions, "libil2cpp.so"), 0x5584D7A00000)

    def test_module_for_address_reports_relative_offset(self):
        got = proc.module_for_address(self.regions, 0x5584D7B00010)
        self.assertIsNotNone(got)
        path, off = got
        self.assertTrue(path.endswith("libil2cpp.so"))
        self.assertEqual(off, 0x100010)

    def test_scannable_regions_drops_readonly_and_device_maps(self):
        keep = proc.scannable_regions(self.regions)
        paths = [r.path for r in keep]
        self.assertIn("[anon:libc_malloc]", paths)
        self.assertIn("/data/app/game/lib/arm64/libil2cpp.so", paths)
        self.assertNotIn("[vvar]", paths)
        self.assertNotIn("/dev/ashmem/whatever", paths)
        # The .so's r-- and r-x mappings are not writable, so only rw- survives.
        self.assertEqual(sum(p.endswith("libil2cpp.so") for p in paths), 1)

    def test_stack_is_opt_in(self):
        default = proc.scannable_regions(self.regions)
        self.assertTrue(any(r.path.startswith("[stack") for r in default))
        without = proc.scannable_regions(self.regions, stack=False)
        self.assertFalse(any(r.path.startswith("[stack") for r in without))


class TestValues(unittest.TestCase):
    def test_roundtrip_every_type(self):
        for name, vt in values.TYPES.items():
            v = 1.5 if vt.is_float else 7
            self.assertTrue(values.equal(vt, vt.unpack(vt.pack(v)), v), name)

    def test_hex_parsing(self):
        self.assertEqual(values.get("i32").parse("0x1f4"), 500)
        self.assertEqual(values.get("i32").parse("500"), 500)

    def test_float_comparison_tolerates_imprecision(self):
        f32 = values.get("f32")
        self.assertTrue(values.equal(f32, f32.unpack(f32.pack(101.5)), 101.5))
        self.assertFalse(values.equal(f32, 101.5, 101.7))

    def test_iter_values_respects_alignment(self):
        vt = values.get("i32")
        buf = b"\x01\x00\x00\x00\x02\x00\x00\x00"
        self.assertEqual([v for _, v in vt.iter_values(buf, 4)], [1, 2])
        self.assertEqual(len(list(vt.iter_values(buf, 1))), 5)


class TestExpr(unittest.TestCase):
    def test_arithmetic_and_comparison(self):
        self.assertTrue(expr.evaluate("hp < hp_max * 0.4", {"hp": 30, "hp_max": 100}))
        self.assertFalse(expr.evaluate("hp < hp_max * 0.4", {"hp": 80, "hp_max": 100}))

    def test_boolean_chaining(self):
        env = {"hp": 90, "target": 0, "mp": 50}
        self.assertTrue(expr.evaluate("hp > 50 and target == 0", env))
        self.assertTrue(expr.evaluate("not target or mp > 200", env))

    def test_chained_comparison(self):
        self.assertTrue(expr.evaluate("0 < hp < 100", {"hp": 50}))
        self.assertFalse(expr.evaluate("0 < hp < 100", {"hp": 150}))

    def test_whitelisted_functions_only(self):
        self.assertEqual(expr.evaluate("max(hp, 10)", {"hp": 3}), 10)
        with self.assertRaises(expr.ExprError):
            expr.evaluate("__import__('os').system('id')", {})
        with self.assertRaises(expr.ExprError):
            expr.evaluate("open('/etc/passwd')", {})

    def test_attribute_access_is_rejected(self):
        with self.assertRaises(expr.ExprError):
            expr.evaluate("hp.__class__", {"hp": 1})

    def test_unknown_name_is_an_error_not_a_silent_zero(self):
        with self.assertRaises(expr.ExprError):
            expr.evaluate("mana > 5", {"hp": 1})

    def test_referenced_names(self):
        self.assertEqual(
            expr.referenced_names("hp < 0.4 * hp_max and max(mp, 1) > 0"),
            {"hp", "hp_max", "mp"},
        )


class TestTable(unittest.TestCase):
    def test_roundtrip(self):
        t = OffsetTable(process="com.example.game")
        t.put(Entry("hp", "i32", Chain("/lib/libil2cpp.so", 0x1A2B30, [0x18, 0x40, 0xC]),
                    note="karakter cani"))
        with tempfile.TemporaryDirectory() as d:
            path = os.path.join(d, "offsets.json")
            t.save(path)
            back = OffsetTable.load(path)
        self.assertEqual(back.process, "com.example.game")
        e = back.entries["hp"]
        self.assertEqual(e.chain.offsets, [0x18, 0x40, 0xC])
        self.assertEqual(e.chain.base_offset, 0x1A2B30)
        self.assertEqual(e.note, "karakter cani")

    def test_missing_file_loads_empty(self):
        self.assertEqual(OffsetTable.load("/nonexistent/offsets.json").entries, {})

    def test_chain_str_is_readable(self):
        c = Chain("/data/app/lib/libil2cpp.so", 0x1A2B30, [0x18, 0xC])
        self.assertEqual(str(c), "[libil2cpp.so+0x1A2B30] -> 0x18 + 0xC")


class TestScanPersistence(unittest.TestCase):
    def test_scan_roundtrip(self):
        s = Scan(pid=42, process="game", type_name="f32", align=4,
                 filters=ScanFilters(stack=True))
        s.candidates = {0x1000: 1.5, 0x2000: 2.5}
        s.history = ["first=1.5"]
        with tempfile.TemporaryDirectory() as d:
            path = os.path.join(d, "scan.json")
            s.save(path)
            back = Scan.load(path)
        self.assertEqual(back.candidates, {0x1000: 1.5, 0x2000: 2.5})
        self.assertTrue(back.filters.stack)
        self.assertEqual(back.type_name, "f32")


if __name__ == "__main__":
    unittest.main()
