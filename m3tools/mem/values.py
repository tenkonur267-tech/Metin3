"""Value type codecs used by the scanner."""

from __future__ import annotations

import struct
from dataclasses import dataclass
from typing import Any, Callable


@dataclass(frozen=True)
class ValueType:
    name: str
    fmt: str
    size: int
    parse: Callable[[str], Any]
    is_float: bool = False

    def pack(self, value: Any) -> bytes:
        return struct.pack("<" + self.fmt, value)

    def unpack(self, raw: bytes) -> Any:
        return struct.unpack_from("<" + self.fmt, raw)[0]

    def iter_values(self, buf: bytes, align: int):
        """Yield (offset, value) for every aligned slot in buf."""
        unpack_from = struct.Struct("<" + self.fmt).unpack_from
        end = len(buf) - self.size + 1
        for off in range(0, max(end, 0), align):
            yield off, unpack_from(buf, off)[0]


def _int(s: str) -> int:
    s = s.strip()
    return int(s, 16) if s.lower().startswith("0x") else int(s)


TYPES: dict[str, ValueType] = {
    "i8": ValueType("i8", "b", 1, _int),
    "u8": ValueType("u8", "B", 1, _int),
    "i16": ValueType("i16", "h", 2, _int),
    "u16": ValueType("u16", "H", 2, _int),
    "i32": ValueType("i32", "i", 4, _int),
    "u32": ValueType("u32", "I", 4, _int),
    "i64": ValueType("i64", "q", 8, _int),
    "u64": ValueType("u64", "Q", 8, _int),
    "f32": ValueType("f32", "f", 4, float, is_float=True),
    "f64": ValueType("f64", "d", 8, float, is_float=True),
}

# What a value is worth comparing against, given float imprecision.
FLOAT_EPSILON = 1e-3


def get(name: str) -> ValueType:
    try:
        return TYPES[name]
    except KeyError:
        raise SystemExit(
            f"bilinmeyen tip '{name}'. Secenekler: {', '.join(TYPES)}"
        ) from None


def equal(vt: ValueType, a: Any, b: Any, eps: float = FLOAT_EPSILON) -> bool:
    if vt.is_float:
        return abs(a - b) <= eps
    return a == b
