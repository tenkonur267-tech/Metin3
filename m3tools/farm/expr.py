"""A deliberately small expression evaluator for bot rules.

Rules live in a JSON config the user edits, so `eval` is not an option: this
walks a whitelisted AST instead. Supports arithmetic, comparisons, and/or/not,
and a handful of maths helpers over the named values in the offset table.
"""

from __future__ import annotations

import ast
import operator
from typing import Any, Mapping

_BINOPS = {
    ast.Add: operator.add, ast.Sub: operator.sub, ast.Mult: operator.mul,
    ast.Div: operator.truediv, ast.FloorDiv: operator.floordiv,
    ast.Mod: operator.mod, ast.Pow: operator.pow,
}
_CMPOPS = {
    ast.Eq: operator.eq, ast.NotEq: operator.ne, ast.Lt: operator.lt,
    ast.LtE: operator.le, ast.Gt: operator.gt, ast.GtE: operator.ge,
}
_UNARY = {ast.USub: operator.neg, ast.UAdd: operator.pos, ast.Not: operator.not_}

FUNCS: Mapping[str, Any] = {
    "abs": abs, "min": min, "max": max, "round": round,
    "int": int, "float": float, "bool": bool,
}


class ExprError(ValueError):
    pass


def evaluate(expr: str, names: Mapping[str, Any]) -> Any:
    try:
        tree = ast.parse(expr, mode="eval")
    except SyntaxError as e:
        raise ExprError(f"gecersiz ifade {expr!r}: {e}") from e
    return _eval(tree.body, names)


def _eval(node: ast.AST, names: Mapping[str, Any]) -> Any:
    if isinstance(node, ast.Constant):
        return node.value
    if isinstance(node, ast.Name):
        if node.id in names:
            return names[node.id]
        if node.id in ("True", "False", "None"):
            return {"True": True, "False": False, "None": None}[node.id]
        raise ExprError(f"bilinmeyen isim {node.id!r} - offset tablosunda yok")
    if isinstance(node, ast.BinOp) and type(node.op) in _BINOPS:
        return _BINOPS[type(node.op)](_eval(node.left, names), _eval(node.right, names))
    if isinstance(node, ast.UnaryOp) and type(node.op) in _UNARY:
        return _UNARY[type(node.op)](_eval(node.operand, names))
    if isinstance(node, ast.BoolOp):
        vals = (_eval(v, names) for v in node.values)
        if isinstance(node.op, ast.And):
            return all(vals)
        return any(vals)
    if isinstance(node, ast.Compare):
        left = _eval(node.left, names)
        for op, comp in zip(node.ops, node.comparators):
            if type(op) not in _CMPOPS:
                raise ExprError(f"desteklenmeyen karsilastirma {type(op).__name__}")
            right = _eval(comp, names)
            if not _CMPOPS[type(op)](left, right):
                return False
            left = right
        return True
    if isinstance(node, ast.Call) and isinstance(node.func, ast.Name):
        fn = FUNCS.get(node.func.id)
        if fn is None:
            raise ExprError(f"izin verilmeyen fonksiyon {node.func.id!r}")
        return fn(*(_eval(a, names) for a in node.args))
    if isinstance(node, ast.IfExp):
        return _eval(node.body if _eval(node.test, names) else node.orelse, names)
    raise ExprError(f"desteklenmeyen ifade ogesi {type(node).__name__}")


def referenced_names(expr: str) -> set[str]:
    """Names a rule needs, so the bot can validate a config before running."""
    try:
        tree = ast.parse(expr, mode="eval")
    except SyntaxError:
        return set()
    return {
        n.id for n in ast.walk(tree)
        if isinstance(n, ast.Name) and n.id not in FUNCS
        and n.id not in ("True", "False", "None")
    }
