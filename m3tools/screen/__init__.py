"""Screen capture: frame decoding, PNG export, and device shells.

Useful in both automation paths - a screenshot is how tap coordinates get
calibrated, whether the bot reads game state from memory or from pixels.
"""

from .capture import Screen
from .frame import Frame, FrameError
from .probe import Probe, load_probes
from .shell import Shell, auto_shell

__all__ = ["Screen", "Frame", "FrameError", "Probe", "load_probes",
           "Shell", "auto_shell"]
