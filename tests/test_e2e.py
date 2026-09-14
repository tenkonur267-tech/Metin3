"""End-to-end test: scan a known target, then rediscover its pointer chain.

Compiles tests/target.c, which builds a three-level pointer chain rooted in a
global, then drives the real scanner against it exactly as a user would.
"""

from __future__ import annotations

import os
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from m3tools.mem import proc
from m3tools.mem.pointers import PointerMap, find_chains, verify_chains
from m3tools.mem.rw import ProcessMemory, can_ptrace
from m3tools.mem.scanner import Scan

HERE = os.path.dirname(os.path.abspath(__file__))


def build_target(tmpdir: str) -> str:
    exe = os.path.join(tmpdir, "target")
    subprocess.run(
        ["gcc", "-O0", "-g", "-o", exe, os.path.join(HERE, "target.c")],
        check=True,
    )
    return exe


class Target:
    def __init__(self, exe: str, hp: int = 1500):
        self.proc = subprocess.Popen(
            [exe, str(hp)],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            text=True,
            bufsize=1,
        )
        self.info = {}
        for _ in range(5):
            key, _, val = self.proc.stdout.readline().strip().partition("=")
            self.info[key] = val
        self.pid = int(self.info["pid"])
        self.hp_addr = int(self.info["hp"], 16)
        self.gold_addr = int(self.info["gold"], 16)

    def set_hp(self, hp: int) -> None:
        self.proc.stdin.write(f"hp {hp}\n")
        self.proc.stdin.flush()
        self.proc.stdout.readline()

    def stop(self) -> None:
        try:
            self.proc.stdin.write("quit\n")
            self.proc.stdin.flush()
            self.proc.wait(timeout=5)
        except Exception:
            self.proc.kill()
        finally:
            for stream in (self.proc.stdin, self.proc.stdout):
                try:
                    stream.close()
                except Exception:
                    pass


@unittest.skipUnless(sys.platform.startswith("linux"), "linux only")
class TestEndToEnd(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        cls.exe = build_target(cls.tmp.name)

    @classmethod
    def tearDownClass(cls):
        cls.tmp.cleanup()

    def setUp(self):
        self.target = Target(self.exe)
        ok, why = can_ptrace(self.target.pid)
        if not ok:
            self.target.stop()
            self.skipTest(f"hedef okunamiyor: {why}")
        self.addCleanup(self.target.stop)

    def test_scan_narrows_to_the_real_address(self):
        scan = Scan(pid=self.target.pid, process="target", type_name="i32")
        with ProcessMemory(self.target.pid) as mem:
            n = scan.first(mem, 1500)
            self.assertGreater(n, 0, "1500 degeri hic bulunamadi")
            self.assertIn(self.target.hp_addr, scan.candidates)

            for hp in (1320, 1180, 947):
                self.target.set_hp(hp)
                scan.refine(mem, "eq", hp)
                self.assertIn(self.target.hp_addr, scan.candidates)

            self.assertLessEqual(len(scan.candidates), 4, scan.describe())

    def test_unknown_value_search(self):
        scan = Scan(pid=self.target.pid, process="target", type_name="i32")
        with ProcessMemory(self.target.pid) as mem:
            scan.first(mem, None)
            self.target.set_hp(1400)
            scan.refine(mem, "decreased")
            self.target.set_hp(1399)
            scan.refine(mem, "decreased")
            self.target.set_hp(1399)
            scan.refine(mem, "unchanged")
            self.assertIn(self.target.hp_addr, scan.candidates)

    def test_pointer_chain_is_found_and_resolves(self):
        with ProcessMemory(self.target.pid) as mem:
            pm = PointerMap.build(mem)
            self.assertGreater(len(pm), 0)

            chains = find_chains(pm, self.target.hp_addr, max_depth=5,
                                 max_offset=0x200)
            self.assertTrue(chains, "hic pointer zinciri bulunamadi")

            good = verify_chains(mem, chains, self.target.hp_addr)
            self.assertTrue(good, "bulunan zincirlerin hicbiri dogrulanmadi")

            # The target's chain is g_root -> +0x40 -> +0x18 -> hp at +0x0.
            depths = {c.depth for c in good}
            self.assertIn(3, depths, f"beklenen 3 seviyeli zincir yok: {[str(c) for c in good]}")
            exact = [c for c in good if c.offsets == [0x40, 0x18, 0x00]]
            self.assertTrue(exact, f"beklenen offsetler bulunamadi: {[str(c) for c in good]}")

    def test_chain_survives_a_restart(self):
        """The whole point of a chain: it works on a fresh process too."""
        with ProcessMemory(self.target.pid) as mem:
            pm = PointerMap.build(mem)
            chains = verify_chains(
                mem,
                find_chains(pm, self.target.hp_addr, max_depth=5, max_offset=0x200),
                self.target.hp_addr,
            )
        chain = next(c for c in chains if c.offsets == [0x40, 0x18, 0x00])

        self.target.stop()
        self.target = Target(self.exe, hp=777)
        self.addCleanup(self.target.stop)
        with ProcessMemory(self.target.pid) as mem:
            addr = chain.resolve(mem)
            self.assertEqual(addr, self.target.hp_addr)
            self.assertEqual(int.from_bytes(mem.read(addr, 4), "little"), 777)

    def test_write_changes_the_game_state(self):
        with ProcessMemory(self.target.pid) as mem:
            mem.write(self.target.gold_addr, (999).to_bytes(4, "little"))
            self.assertEqual(
                int.from_bytes(mem.read(self.target.gold_addr, 4), "little"), 999
            )

    def test_module_resolution(self):
        regions = proc.read_maps(self.target.pid)
        self.assertTrue(proc.modules(regions))
        where = proc.module_for_address(regions, self.target.hp_addr)
        # hp lives on the heap, so it belongs to no module.
        self.assertIsNone(where)


if __name__ == "__main__":
    unittest.main()
