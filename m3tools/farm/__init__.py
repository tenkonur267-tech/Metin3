"""Farm automation built on top of the resolved offset table."""

from .bot import Bot, BotConfig, Rule
from .input import auto_backend

__all__ = ["Bot", "BotConfig", "Rule", "auto_backend"]
