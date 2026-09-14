"""Reading game state out of pixels.

A probe turns a region of the screen into a number the rule engine can compare,
without knowing anything about the game's internals. Three kinds cover almost
every HUD:

  bar    - how far a health/mana bar is filled, as a percentage
  color  - whether one point matches a colour (a button lit, an overlay shown)
  bright - mean luminance of a box (a dimmed screen, a popup)
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from .frame import Frame


def distance(a: tuple[int, int, int], b: tuple[int, int, int]) -> float:
    """Euclidean distance in RGB - crude, but stable under mild compression."""
    return ((a[0] - b[0]) ** 2 + (a[1] - b[1]) ** 2 + (a[2] - b[2]) ** 2) ** 0.5


@dataclass
class Probe:
    name: str
    kind: str
    params: dict

    @classmethod
    def from_json(cls, name: str, d: dict) -> "Probe":
        kind = d.get("type")
        if kind not in ("bar", "color", "bright"):
            raise SystemExit(f"probe '{name}': bilinmeyen tip {kind!r}")
        return cls(name, kind, d)

    def read(self, frame: Frame) -> float:
        if self.kind == "bar":
            return self._bar(frame)
        if self.kind == "color":
            return self._color(frame)
        return self._bright(frame)

    # -- kinds -------------------------------------------------------------
    def _bar(self, frame: Frame) -> float:
        """Percentage of the bar still filled.

        Walks the line and stops at the first run of non-matching pixels, so a
        bar whose empty part happens to share the fill colour elsewhere on the
        line does not inflate the reading.
        """
        x0, y0 = self.params["from"]
        x1, y1 = self.params["to"]
        want = tuple(self.params["color"])
        tol = float(self.params.get("tolerance", 60))
        # How many consecutive misses end the filled run; a couple of pixels of
        # border or gloss should not cut the bar short.
        run = int(self.params.get("gap", 3))

        steps = max(abs(x1 - x0), abs(y1 - y0))
        if steps == 0:
            return 0.0
        filled = 0
        misses = 0
        for i in range(steps + 1):
            x = round(x0 + (x1 - x0) * i / steps)
            y = round(y0 + (y1 - y0) * i / steps)
            try:
                px = frame.rgb(x, y)
            except Exception:
                break
            if distance(px, want) <= tol:
                filled = i + 1
                misses = 0
            else:
                misses += 1
                if misses >= run:
                    break
        return 100.0 * filled / (steps + 1)

    def _color(self, frame: Frame) -> float:
        x, y = self.params["at"]
        want = tuple(self.params["color"])
        tol = float(self.params.get("tolerance", 40))
        try:
            return 1.0 if distance(frame.rgb(x, y), want) <= tol else 0.0
        except Exception:
            return 0.0

    def _bright(self, frame: Frame) -> float:
        x0, y0 = self.params["from"]
        x1, y1 = self.params["to"]
        step = int(self.params.get("step", 4))
        total = 0.0
        count = 0
        for y in range(min(y0, y1), max(y0, y1) + 1, step):
            for x in range(min(x0, x1), max(x0, x1) + 1, step):
                try:
                    r, g, b = frame.rgb(x, y)
                except Exception:
                    continue
                total += 0.299 * r + 0.587 * g + 0.114 * b
                count += 1
        return total / count if count else 0.0


def load_probes(d: dict[str, Any]) -> dict[str, Probe]:
    return {name: Probe.from_json(name, cfg) for name, cfg in d.items()}
