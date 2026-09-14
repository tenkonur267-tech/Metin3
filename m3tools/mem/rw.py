"""Reading and writing another process's memory.

Two backends are tried, in order:
  1. process_vm_readv/process_vm_writev  - one syscall, no seek, fastest
  2. /proc/<pid>/mem                     - pread/pwrite on a fd

Both need the caller to be allowed to ptrace the target. On Android that
means root (`su`), since apps run as distinct uids.
"""

from __future__ import annotations

import ctypes
import ctypes.util
import os
from . import proc
from .base import MemoryHandle


class MemoryError_(RuntimeError):
    pass


class _IOVec(ctypes.Structure):
    _fields_ = [("iov_base", ctypes.c_void_p), ("iov_len", ctypes.c_size_t)]


def _load_libc():
    try:
        libc = ctypes.CDLL(ctypes.util.find_library("c") or "libc.so", use_errno=True)
        libc.process_vm_readv.argtypes = [
            ctypes.c_int, ctypes.POINTER(_IOVec), ctypes.c_ulong,
            ctypes.POINTER(_IOVec), ctypes.c_ulong, ctypes.c_ulong,
        ]
        libc.process_vm_readv.restype = ctypes.c_ssize_t
        libc.process_vm_writev.argtypes = libc.process_vm_readv.argtypes
        libc.process_vm_writev.restype = ctypes.c_ssize_t
        return libc
    except (OSError, AttributeError):
        return None


_LIBC = _load_libc()


class ProcessMemory(MemoryHandle):
    """Handle on a process in this same kernel namespace."""

    def __init__(self, pid: int, prefer_syscall: bool = True):
        self.pid = pid
        self._use_syscall = prefer_syscall and _LIBC is not None
        self._fd: int | None = None
        if not os.path.isdir(f"/proc/{pid}"):
            raise MemoryError_(f"pid {pid} yok")

    # -- lifecycle ---------------------------------------------------------
    def _fd_open(self) -> int:
        if self._fd is None:
            try:
                self._fd = os.open(f"/proc/{self.pid}/mem", os.O_RDWR)
            except PermissionError:
                self._fd = os.open(f"/proc/{self.pid}/mem", os.O_RDONLY)
        return self._fd

    def close(self) -> None:
        if self._fd is not None:
            os.close(self._fd)
            self._fd = None

    @property
    def alive(self) -> bool:
        return os.path.isdir(f"/proc/{self.pid}")

    def read_maps(self):
        return proc.read_maps(self.pid)

    # -- reading -----------------------------------------------------------
    def read(self, addr: int, size: int) -> bytes:
        """Read exactly `size` bytes; raises on partial or failed reads."""
        if size <= 0:
            return b""
        if self._use_syscall:
            buf = (ctypes.c_char * size)()
            local = _IOVec(ctypes.cast(buf, ctypes.c_void_p), size)
            remote = _IOVec(ctypes.c_void_p(addr), size)
            n = _LIBC.process_vm_readv(
                self.pid, ctypes.byref(local), 1, ctypes.byref(remote), 1, 0
            )
            if n == size:
                return bytes(buf)
            if n < 0:
                # Some kernels / SELinux policies block the syscall but still
                # allow /proc/pid/mem, so fall through to the fd backend.
                self._use_syscall = False
            else:
                # Short read means the range straddles an unmapped page.
                raise MemoryError_(f"0x{addr:x} kismi okuma ({n}/{size})")
        fd = self._fd_open()
        try:
            data = os.pread(fd, size, addr)
        except OSError as e:
            raise MemoryError_(f"0x{addr:x} okunamadi: {e}") from e
        if len(data) != size:
            raise MemoryError_(f"0x{addr:x} kismi okuma ({len(data)}/{size})")
        return data

    # -- writing -----------------------------------------------------------
    def write(self, addr: int, data: bytes) -> None:
        if not data:
            return
        if self._use_syscall:
            buf = ctypes.create_string_buffer(data, len(data))
            local = _IOVec(ctypes.cast(buf, ctypes.c_void_p), len(data))
            remote = _IOVec(ctypes.c_void_p(addr), len(data))
            n = _LIBC.process_vm_writev(
                self.pid, ctypes.byref(local), 1, ctypes.byref(remote), 1, 0
            )
            if n == len(data):
                return
            self._use_syscall = False
        fd = self._fd_open()
        try:
            written = os.pwrite(fd, data, addr)
        except OSError as e:
            raise MemoryError_(f"0x{addr:x} yazilamadi: {e}") from e
        if written != len(data):
            raise MemoryError_(f"0x{addr:x} kismi yazma ({written}/{len(data)})")


def can_ptrace(pid: int) -> tuple[bool, str]:
    """Cheap up-front check so the CLI can explain *why* it cannot attach."""
    if not os.path.isdir(f"/proc/{pid}"):
        return False, "surec yok"
    try:
        with open(f"/proc/{pid}/mem", "rb") as fh:
            fh.seek(0)
    except PermissionError:
        return False, (
            "izin yok - hedef baska bir uid altinda calisiyor. "
            "Termux'u root ile calistirin (`su`) veya ADB/Shizuku kullanin."
        )
    except OSError:
        pass
    return True, "ok"
