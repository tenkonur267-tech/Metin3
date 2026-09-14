"""Shared behaviour for local and remote memory handles.

Scanning a whole address space means reading across holes: a region can be
mapped yet contain pages the kernel will not hand over. Both backends need the
same tolerance for that, and the same idea of how much to read at once - so it
lives here rather than being written twice.
"""

from __future__ import annotations


class MemoryHandle:
    pid: int
    # How far apart two candidate addresses may be before the scanner reads
    # them separately. A local read is a syscall; a remote one is a process
    # spawn plus a round trip, so remote backends raise this considerably.
    batch_span: int = 65536
    # Bytes per bulk read while sweeping regions.
    chunk_size: int = 4 << 20

    def read(self, addr: int, size: int) -> bytes:
        raise NotImplementedError

    def write(self, addr: int, data: bytes) -> None:
        raise NotImplementedError

    def read_maps(self):
        raise NotImplementedError

    @property
    def alive(self) -> bool:
        raise NotImplementedError

    def close(self) -> None:
        pass

    def __enter__(self):
        return self

    def __exit__(self, *exc) -> None:
        self.close()

    # -- derived ----------------------------------------------------------
    def try_read(self, addr: int, size: int) -> bytes | None:
        try:
            return self.read(addr, size)
        except Exception:
            return None

    def read_ptr(self, addr: int, ptr_size: int = 8) -> int | None:
        raw = self.try_read(addr, ptr_size)
        return None if raw is None else int.from_bytes(raw, "little")

    def read_chunks(self, addr: int, total: int, chunk: int | None = None):
        """Yield (offset, bytes) over a range, stepping around unreadable pages."""
        chunk = chunk or self.chunk_size
        done = 0
        while done < total:
            n = min(chunk, total - done)
            data = self.try_read(addr + done, n)
            if data is None:
                if n <= 4096:
                    done += n
                    continue
                # Halve and retry: the hole is somewhere in this span, and
                # bisecting finds the readable part without dropping it all.
                half = (n // 2) & ~0xFFF or 4096
                data = self.try_read(addr + done, half)
                if data is None:
                    done += 4096
                    continue
                yield done, data
                done += half
                continue
            yield done, data
            done += n
