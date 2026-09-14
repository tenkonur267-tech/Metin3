"""Low-level memory inspection primitives for a running Android game."""

from .proc import Region, find_pids, module_base, read_maps, resolve_pid
from .pointers import Chain, PointerMap, find_chains, verify_chains
from .rw import ProcessMemory, can_ptrace
from .scanner import Scan, ScanFilters
from .table import Entry, OffsetTable, ResolvedTable

__all__ = [
    "Region", "find_pids", "module_base", "read_maps", "resolve_pid",
    "Chain", "PointerMap", "find_chains", "verify_chains",
    "ProcessMemory", "can_ptrace",
    "Scan", "ScanFilters",
    "Entry", "OffsetTable", "ResolvedTable",
]
