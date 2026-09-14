"""Tests for pixel probes and the screen state source."""

from __future__ import annotations

import os
import struct
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from m3tools.farm import BotConfig, ScreenSource
from m3tools.farm.bot import Bot
from m3tools.screen.frame import Frame
from m3tools.screen.probe import Probe, load_probes


def frame_from_rows(rows: list[list[tuple[int, int, int]]]) -> Frame:
    h = len(rows)
    w = len(rows[0])
    px = bytearray()
    for row in rows:
        for r, g, b in row:
            px += bytes((r, g, b, 255))
    return Frame(w, h, 1, bytes(px))


RED = (200, 40, 40)
GREY = (60, 60, 60)


class TestBarProbe(unittest.TestCase):
    def make(self, filled: int, width: int = 100) -> Frame:
        row = [RED] * filled + [GREY] * (width - filled)
        return frame_from_rows([row])

    def probe(self, width: int = 100, **extra) -> Probe:
        cfg = {"type": "bar", "from": [0, 0], "to": [width - 1, 0],
               "color": list(RED), "tolerance": 60}
        cfg.update(extra)
        return Probe.from_json("hp", cfg)

    def test_full_bar_reads_100(self):
        self.assertAlmostEqual(self.probe().read(self.make(100)), 100.0, places=1)

    def test_empty_bar_reads_0(self):
        self.assertEqual(self.probe().read(self.make(0)), 0.0)

    def test_half_bar_reads_about_50(self):
        self.assertAlmostEqual(self.probe().read(self.make(50)), 50.0, delta=2)

    def test_stops_at_the_first_gap(self):
        """A second red patch further along must not inflate the reading."""
        row = [RED] * 30 + [GREY] * 40 + [RED] * 30
        got = self.probe().read(frame_from_rows([row]))
        self.assertAlmostEqual(got, 30.0, delta=2)

    def test_small_gaps_are_tolerated(self):
        # Two grey gloss pixels inside the fill should not cut it short.
        row = [RED] * 20 + [GREY] * 2 + [RED] * 28 + [GREY] * 50
        got = self.probe(gap=4).read(frame_from_rows([row]))
        self.assertAlmostEqual(got, 50.0, delta=2)

    def test_tolerance_controls_matching(self):
        row = [(160, 30, 30)] * 50 + [GREY] * 50
        self.assertAlmostEqual(self.probe(tolerance=60).read(frame_from_rows([row])),
                               50.0, delta=2)
        self.assertEqual(self.probe(tolerance=5).read(frame_from_rows([row])), 0.0)

    def test_zero_length_bar_does_not_divide_by_zero(self):
        p = Probe.from_json("x", {"type": "bar", "from": [5, 0], "to": [5, 0],
                                  "color": list(RED)})
        self.assertEqual(p.read(self.make(100)), 0.0)

    def test_vertical_bar(self):
        rows = [[RED] for _ in range(40)] + [[GREY] for _ in range(60)]
        p = Probe.from_json("hp", {"type": "bar", "from": [0, 0], "to": [0, 99],
                                   "color": list(RED), "tolerance": 60})
        self.assertAlmostEqual(p.read(frame_from_rows(rows)), 40.0, delta=2)


class TestOtherProbes(unittest.TestCase):
    def test_color_probe_is_binary(self):
        f = frame_from_rows([[RED, GREY]])
        hit = Probe.from_json("x", {"type": "color", "at": [0, 0],
                                    "color": list(RED), "tolerance": 30})
        miss = Probe.from_json("x", {"type": "color", "at": [1, 0],
                                     "color": list(RED), "tolerance": 30})
        self.assertEqual(hit.read(f), 1.0)
        self.assertEqual(miss.read(f), 0.0)

    def test_color_probe_off_screen_is_zero_not_a_crash(self):
        f = frame_from_rows([[RED]])
        p = Probe.from_json("x", {"type": "color", "at": [99, 99],
                                  "color": list(RED)})
        self.assertEqual(p.read(f), 0.0)

    def test_bright_probe_averages_luminance(self):
        white = frame_from_rows([[(255, 255, 255)] * 4] * 4)
        black = frame_from_rows([[(0, 0, 0)] * 4] * 4)
        p = Probe.from_json("x", {"type": "bright", "from": [0, 0],
                                  "to": [3, 3], "step": 1})
        self.assertAlmostEqual(p.read(white), 255.0, delta=1)
        self.assertEqual(p.read(black), 0.0)

    def test_unknown_probe_type_is_rejected(self):
        with self.assertRaises(SystemExit):
            Probe.from_json("x", {"type": "sihirli"})


class FakeScreen:
    def __init__(self, frame):
        self.frame = frame
        self.captures = 0

    def capture(self):
        self.captures += 1
        return self.frame


class TestScreenSource(unittest.TestCase):
    def build(self):
        src = ScreenSource.__new__(ScreenSource)
        src.screen = FakeScreen(frame_from_rows([[RED] * 50 + [GREY] * 50]))
        src.probes = load_probes({
            "hp_pct": {"type": "bar", "from": [0, 0], "to": [99, 0],
                       "color": list(RED), "tolerance": 60},
            "flag": {"type": "color", "at": [0, 0], "color": list(RED)},
        })
        src.save_last = None
        src.last_frame = None
        return src

    def test_values_come_from_one_capture(self):
        src = self.build()
        vals = src.values()
        self.assertEqual(src.screen.captures, 1,
                         "her prob icin ayri capture alinmamali")
        self.assertAlmostEqual(vals["hp_pct"], 50.0, delta=2)
        self.assertEqual(vals["flag"], 1.0)

    def test_write_is_refused_with_a_clear_message(self):
        with self.assertRaises(SystemExit):
            self.build().write("hp_pct", 100)

    def test_bot_validates_rules_against_probe_names(self):
        config = BotConfig(process="x", source="screen",
                           probes={"hp_pct": {"type": "bar", "from": [0, 0],
                                              "to": [9, 0], "color": [1, 2, 3]}})
        config.rules = []
        bot = Bot(config, self.build(), dry_run=True)
        from m3tools.farm.bot import Rule
        bot.config.rules = [Rule(name="bad", when="mana < 5", do=[]),
                            Rule(name="good", when="hp_pct < 50", do=[])]
        problems = bot.validate()
        self.assertEqual(len(problems), 1)
        self.assertIn("mana", problems[0])


if __name__ == "__main__":
    unittest.main()
