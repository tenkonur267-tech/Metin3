"""The farm loop: read named values, evaluate rules, inject input.

Everything game-specific lives in a JSON config plus the offset table, so the
same loop works whatever the client turns out to look like.
"""

from __future__ import annotations

import json
import os
import time
from dataclasses import dataclass, field
from typing import Any

from ..mem import proc
from ..mem.rw import ProcessMemory
from ..mem.table import OffsetTable, ResolvedTable
from . import expr
from .input import InputBackend, auto_backend


@dataclass
class Rule:
    name: str
    when: str
    do: list[dict]
    cooldown: float = 1.0
    priority: int = 0
    enabled: bool = True
    _last_fired: float = field(default=0.0, repr=False)

    @classmethod
    def from_json(cls, d: dict) -> "Rule":
        return cls(
            name=d["name"],
            when=d.get("when", "True"),
            do=d.get("do", []),
            cooldown=float(d.get("cooldown", 1.0)),
            priority=int(d.get("priority", 0)),
            enabled=bool(d.get("enabled", True)),
        )

    def ready(self, now: float) -> bool:
        return self.enabled and now - self._last_fired >= self.cooldown


@dataclass
class BotConfig:
    process: str
    poll_interval: float = 0.25
    rules: list[Rule] = field(default_factory=list)
    # Derived values the rules can reference, e.g. "hp_pct": "100*hp/hp_max"
    derived: dict[str, str] = field(default_factory=dict)
    # Named screen coordinates so taps read as "tap: attack" not "tap: 980,1650"
    points: dict[str, list[int]] = field(default_factory=dict)
    stop_after: float = 0.0
    backend: str = "auto"

    @classmethod
    def load(cls, path: str) -> "BotConfig":
        with open(path) as fh:
            d = json.load(fh)
        return cls(
            process=d["process"],
            poll_interval=float(d.get("poll_interval", 0.25)),
            rules=[Rule.from_json(r) for r in d.get("rules", [])],
            derived=d.get("derived", {}),
            points={k: list(v) for k, v in d.get("points", {}).items()},
            stop_after=float(d.get("stop_after", 0)),
            backend=d.get("backend", "auto"),
        )


class Bot:
    def __init__(
        self,
        config: BotConfig,
        table: OffsetTable,
        *,
        dry_run: bool = False,
        verbose: bool = False,
    ):
        self.config = config
        self.table = table
        self.dry_run = dry_run
        self.verbose = verbose
        self.pid = proc.resolve_pid(config.process)
        self.mem = ProcessMemory(self.pid)
        self.live = ResolvedTable(table, self.mem)
        self.input: InputBackend | None = None
        if not dry_run:
            self.input = auto_backend(config.backend)
        self.fired: dict[str, int] = {}

    # -- state -------------------------------------------------------------
    def snapshot(self) -> dict[str, Any]:
        """Current value of every table entry, plus derived expressions."""
        names: dict[str, Any] = {}
        for name in self.table.entries:
            v = self.live.read(name)
            names[name] = v if v is not None else 0
        names["_t"] = time.time()
        for key, formula in self.config.derived.items():
            try:
                names[key] = expr.evaluate(formula, names)
            except (expr.ExprError, ZeroDivisionError, TypeError):
                names[key] = 0
        return names

    # -- actions -----------------------------------------------------------
    def _point(self, ref) -> tuple[int, int]:
        if isinstance(ref, str):
            if ref not in self.config.points:
                raise SystemExit(f"tanimsiz nokta '{ref}' - config'teki points'e ekleyin")
            x, y = self.config.points[ref]
        else:
            x, y = ref
        return int(x), int(y)

    def act(self, action: dict, names: dict[str, Any]) -> None:
        kind = action.get("type") or next(iter(action))
        if kind == "tap":
            x, y = self._point(action.get("at", action.get("tap")))
            self._log(f"tap {x},{y}")
            if self.input:
                self.input.tap(x, y, float(action.get("duration", 0.05)))
        elif kind == "swipe":
            x1, y1 = self._point(action["from"])
            x2, y2 = self._point(action["to"])
            self._log(f"swipe {x1},{y1} -> {x2},{y2}")
            if self.input:
                self.input.swipe(x1, y1, x2, y2, float(action.get("duration", 0.3)))
        elif kind == "key":
            code = action.get("code", action.get("key"))
            self._log(f"key {code}")
            if self.input:
                self.input.key(code)
        elif kind == "wait":
            time.sleep(float(action.get("seconds", action.get("wait", 0.5))))
        elif kind == "write":
            # Writing game state is far more detectable than tapping, and it is
            # easy to corrupt the client with a bad value, so it stays opt-in.
            name = action["name"]
            value = action["value"]
            if isinstance(value, str):
                value = expr.evaluate(value, names)
            self._log(f"write {name} = {value}")
            if not self.dry_run:
                self.live.write(name, value)
        elif kind == "log":
            print(action.get("message", "").format(**names))
        else:
            raise SystemExit(f"bilinmeyen eylem '{kind}'")

    def _log(self, msg: str) -> None:
        if self.verbose or self.dry_run:
            print(f"  [{time.strftime('%H:%M:%S')}] {msg}")

    # -- loop --------------------------------------------------------------
    def validate(self) -> list[str]:
        """Report rules referencing values the offset table cannot supply."""
        known = set(self.table.entries) | set(self.config.derived) | {"_t"}
        problems = []
        for rule in self.config.rules:
            missing = expr.referenced_names(rule.when) - known
            if missing:
                problems.append(f"kural '{rule.name}': bilinmeyen isim {sorted(missing)}")
        return problems

    def tick(self) -> None:
        names = self.snapshot()
        now = time.time()
        for rule in sorted(self.config.rules, key=lambda r: -r.priority):
            if not rule.ready(now):
                continue
            try:
                hit = bool(expr.evaluate(rule.when, names))
            except (expr.ExprError, ZeroDivisionError, TypeError) as e:
                self._log(f"kural '{rule.name}' degerlendirilemedi: {e}")
                continue
            if not hit:
                continue
            rule._last_fired = now
            self.fired[rule.name] = self.fired.get(rule.name, 0) + 1
            self._log(f"kural '{rule.name}' tetiklendi")
            for action in rule.do:
                self.act(action, names)
            # One rule per tick keeps the highest-priority action authoritative
            # instead of stacking conflicting taps in the same frame.
            return

    def run(self) -> None:
        problems = self.validate()
        if problems:
            raise SystemExit("config hatalari:\n  " + "\n  ".join(problems))
        started = time.time()
        mode = "DRY-RUN" if self.dry_run else f"backend={self.input.name}"
        print(f"bot basladi: pid {self.pid} ({self.config.process}) [{mode}]")
        try:
            while True:
                if not self.mem.alive:
                    print("oyun sureci kapandi, duruluyor")
                    break
                if self.config.stop_after and time.time() - started > self.config.stop_after:
                    print("stop_after suresi doldu")
                    break
                self.tick()
                time.sleep(self.config.poll_interval)
        except KeyboardInterrupt:
            print("\ndurduruldu")
        finally:
            if self.input:
                self.input.close()
            self.mem.close()
            if self.fired:
                print("tetiklenen kurallar: " + ", ".join(
                    f"{k}x{v}" for k, v in sorted(self.fired.items())))


def default_config_path() -> str:
    return os.path.join(os.environ.get("M3_HOME", os.path.expanduser("~/.m3")),
                        "farm.json")
