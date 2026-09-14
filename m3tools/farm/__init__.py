"""Farm automation over either memory reads or screen probes."""

from .bot import Bot, BotConfig, Rule
from .input import auto_backend
from .sources import MemorySource, ScreenSource, StateSource

__all__ = ["Bot", "BotConfig", "Rule", "auto_backend",
           "MemorySource", "ScreenSource", "StateSource"]
