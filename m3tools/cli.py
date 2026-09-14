#!/usr/bin/env python3
"""m3 - Termux'tan calisan bellek tarayici / pointer bulucu.

Tipik akis:

    m3 ps metin                  # oyunun pid'ini bul
    m3 scan -p metin -t i32 -v 1500   # HP = 1500 iken ara
    (oyunda hasar al)
    m3 next -v 1320                   # yeni degerle daralt
    m3 next -v 1180
    m3 list                           # 1-2 adres kalinca
    m3 pointer 0x7b1c2d40f0 --name hp --type i32
    m3 table                          # kalici offset tablosu
"""

from __future__ import annotations

import argparse
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from m3tools.mem import proc, values
from m3tools.mem.pointers import Chain, PointerMap, find_chains, verify_chains
from m3tools.mem.rw import ProcessMemory, can_ptrace
from m3tools.mem.scanner import Scan, ScanFilters
from m3tools.mem.table import Entry, OffsetTable, ResolvedTable

STATE_DIR = os.environ.get("M3_HOME", os.path.expanduser("~/.m3"))
SCAN_FILE = os.path.join(STATE_DIR, "scan.json")
PMAP_FILE = os.path.join(STATE_DIR, "pointermap.bin")
TABLE_FILE = os.path.join(STATE_DIR, "offsets.json")


def _attach(pid: int) -> ProcessMemory:
    ok, why = can_ptrace(pid)
    if not ok:
        raise SystemExit(f"pid {pid} okunamiyor: {why}")
    return ProcessMemory(pid)


def _load_scan() -> Scan:
    if not os.path.exists(SCAN_FILE):
        raise SystemExit("acik tarama yok - once 'm3 scan' calistirin.")
    scan = Scan.load(SCAN_FILE)
    if not os.path.isdir(f"/proc/{scan.pid}"):
        raise SystemExit(
            f"taramadaki pid {scan.pid} artik yok (oyun yeniden baslatildi). "
            "Yeni 'm3 scan' gerekiyor - kalici adres icin 'm3 pointer' kullanin."
        )
    return scan


def _human(n: int) -> str:
    for unit in ("B", "KB", "MB", "GB"):
        if n < 1024:
            return f"{n:.0f}{unit}"
        n /= 1024
    return f"{n:.1f}TB"


# -- commands --------------------------------------------------------------


def cmd_ps(args) -> None:
    hits = proc.find_pids(args.pattern or "")
    if not hits:
        print("eslesen surec yok")
        return
    for pid, name in hits:
        ok, why = can_ptrace(pid)
        mark = "ok" if ok else why.split(" - ")[0]
        print(f"{pid:>7}  {name:<50} {mark}")


def cmd_maps(args) -> None:
    pid = proc.resolve_pid(args.pid)
    regions = proc.read_maps(pid)
    if args.modules:
        for path, base in sorted(proc.modules(regions).items(), key=lambda kv: kv[1]):
            print(f"0x{base:012x}  {path}")
        return
    scan = proc.scannable_regions(regions)
    total = sum(r.size for r in scan)
    for r in scan if not args.all else regions:
        print(f"{r}  {_human(r.size)}")
    print(f"\n{len(scan)} taranabilir bolge, toplam {_human(total)}")


def cmd_scan(args) -> None:
    pid = proc.resolve_pid(args.pid)
    vt = values.get(args.type)
    filters = ScanFilters(
        heap=not args.no_heap,
        stack=args.stack,
        anon=not args.no_anon,
        libs=not args.no_libs,
    )
    scan = Scan(
        pid=pid,
        process=proc.process_name(pid),
        type_name=args.type,
        align=args.align or vt.size,
        filters=filters,
    )
    if not args.unknown and args.value is None:
        raise SystemExit("-v DEGER verin ya da --unknown kullanin")
    wanted = None if args.unknown else vt.parse(args.value)
    t0 = time.time()
    with _attach(pid) as mem:
        n = scan.first(mem, wanted)
    scan.save(SCAN_FILE)
    print(f"{n} aday ({time.time() - t0:.1f}s)")
    if n and n <= args.show:
        print("\n".join(scan.describe(args.show)))
    elif n:
        print("Oyunda degeri degistirip 'm3 next' ile daraltin.")


def cmd_next(args) -> None:
    scan = _load_scan()
    vt = scan.vtype
    if args.value is not None:
        op, arg = "eq", vt.parse(args.value)
    elif args.between:
        lo, hi = args.between.split(",", 1)
        op, arg = "between", (vt.parse(lo), vt.parse(hi))
    else:
        op, arg = args.op, None
        if op is None:
            raise SystemExit("-v DEGER veya --op {changed,increased,...} verin")
    t0 = time.time()
    with _attach(scan.pid) as mem:
        n = scan.refine(mem, op, arg)
    scan.save(SCAN_FILE)
    print(f"{n} aday kaldi ({time.time() - t0:.1f}s)")
    if n and n <= args.show:
        print("\n".join(scan.describe(args.show)))


def cmd_list(args) -> None:
    scan = _load_scan()
    with _attach(scan.pid) as mem:
        scan.refresh(mem)
    print(f"pid {scan.pid} ({scan.process}) tip {scan.type_name}")
    print("gecmis: " + " -> ".join(scan.history))
    print(f"{len(scan.candidates)} aday")
    print("\n".join(scan.describe(args.show)))


def cmd_watch(args) -> None:
    """Live-tail an address (or the surviving candidates) to sanity-check it."""
    if args.address:
        pid = proc.resolve_pid(args.pid)
        vt = values.get(args.type)
        addr = int(args.address, 0)
        with _attach(pid) as mem:
            while True:
                raw = mem.try_read(addr, vt.size)
                shown = vt.unpack(raw) if raw else "<okunamadi>"
                print(f"\r0x{addr:x} = {shown!r:<24}", end="", flush=True)
                time.sleep(args.interval)
    scan = _load_scan()
    with _attach(scan.pid) as mem:
        while True:
            scan.refresh(mem)
            print("\033[2J\033[H" + "\n".join(scan.describe(args.show)), flush=True)
            time.sleep(args.interval)


def cmd_write(args) -> None:
    pid = proc.resolve_pid(args.pid)
    vt = values.get(args.type)
    with _attach(pid) as mem:
        mem.write(int(args.address, 0), vt.pack(vt.parse(args.value)))
    print("yazildi")


def cmd_pmap(args) -> None:
    pid = proc.resolve_pid(args.pid)
    t0 = time.time()
    last = [0.0]

    def progress(seen, total):
        now = time.time()
        if now - last[0] > 0.5:
            last[0] = now
            pct = 100.0 * seen / total if total else 100.0
            print(f"\r  {pct:5.1f}%  {_human(seen)}/{_human(total)}", end="", flush=True)

    with _attach(pid) as mem:
        pm = PointerMap.build(mem, include_stack=args.stack, progress=progress)
    pm.save(PMAP_FILE)
    print(f"\r{len(pm)} pointer kaydedildi ({time.time() - t0:.1f}s) -> {PMAP_FILE}")


def cmd_pointer(args) -> None:
    pid = proc.resolve_pid(args.pid) if args.pid else _load_scan().pid
    target = int(args.address, 0)

    if os.path.exists(PMAP_FILE) and not args.rebuild:
        pm = PointerMap.load(PMAP_FILE)
        if pm.pid != pid:
            print("kayitli pointer haritasi baska bir pid'e ait, yeniden kuruluyor...")
            pm = None
    else:
        pm = None
    if pm is None:
        with _attach(pid) as mem:
            pm = PointerMap.build(mem)
        pm.save(PMAP_FILE)
    print(f"pointer haritasi: {len(pm)} giris")

    chains = find_chains(
        pm,
        target,
        max_depth=args.depth,
        max_offset=int(args.max_offset, 0),
        max_results=args.max_results,
    )
    if not chains:
        print(
            "zincir bulunamadi. --depth artirin, --max-offset buyutun "
            "veya once 'm3 pmap --stack' ile haritayi genisletin."
        )
        return

    with _attach(pid) as mem:
        good = verify_chains(mem, chains, target)
    print(f"{len(chains)} aday zincir, {len(good)} tanesi su an dogruluyor:\n")
    for i, c in enumerate(good[:args.max_results]):
        print(f"  [{i}] derinlik {c.depth}  {c}")

    if args.name:
        if not good:
            raise SystemExit("dogrulanan zincir yok, tabloya eklenmedi")
        chosen = good[args.pick]
        table = OffsetTable.load(TABLE_FILE)
        table.process = proc.process_name(pid)
        table.put(Entry(args.name, args.type, chosen, args.note or ""))
        table.save(TABLE_FILE)
        print(f"\n'{args.name}' tabloya eklendi -> {TABLE_FILE}")


def cmd_table(args) -> None:
    table = OffsetTable.load(TABLE_FILE)
    if not table.entries:
        print("tablo bos - 'm3 pointer --name ...' ile giris ekleyin")
        return
    if args.remove:
        table.entries.pop(args.remove, None)
        table.save(TABLE_FILE)
        print(f"'{args.remove}' silindi")
        return
    print(f"surec: {table.process}\n")
    live = None
    if args.pid:
        mem = _attach(proc.resolve_pid(args.pid))
        live = ResolvedTable(table, mem)
    for name, e in table.entries.items():
        line = f"{name:<16} {e.type:<5} {e.chain}"
        if live is not None:
            addr = live.address(name)
            val = live.read(name)
            if addr is None:
                line += f"\n{'':<16} -> COZULEMEDI (oyun guncellendi mi?)"
            else:
                line += f"\n{'':<16} -> 0x{addr:x} = {val!r}"
        if e.note:
            line += f"\n{'':<16} # {e.note}"
        print(line)


def cmd_farm(args) -> None:
    from m3tools.farm.bot import Bot, BotConfig, default_config_path

    cfg_path = args.config or default_config_path()
    if not os.path.exists(cfg_path):
        raise SystemExit(
            f"config yok: {cfg_path}\n"
            "Ornegi kopyalayin: cp examples/farm.example.json " + cfg_path
        )
    config = BotConfig.load(cfg_path)
    if args.source:
        config.source = args.source
    table = OffsetTable.load(args.table or TABLE_FILE)
    Bot.build(config, table, dry_run=args.dry_run, verbose=args.verbose).run()


def cmd_probe(args) -> None:
    """Read a config's probes against the live screen, to calibrate them."""
    from m3tools.farm.bot import BotConfig, default_config_path
    from m3tools.screen import load_probes
    from m3tools.screen.capture import Screen
    from m3tools.screen.shell import auto_shell

    cfg_path = args.config or default_config_path()
    if not os.path.exists(cfg_path):
        raise SystemExit(f"config yok: {cfg_path}")
    config = BotConfig.load(cfg_path)
    if not config.probes:
        raise SystemExit("config'te 'probes' bolumu yok")
    probes = load_probes(config.probes)
    screen = Screen(auto_shell(config.backend))
    while True:
        frame = screen.capture()
        line = "  ".join(f"{n}={p.read(frame):7.2f}" for n, p in probes.items())
        print(f"\r{frame}  {line}   ", end="", flush=True)
        if not args.watch:
            print()
            return
        time.sleep(args.interval)


def cmd_shot(args) -> None:
    """Grab a frame; the PNG is how tap coordinates get picked."""
    from m3tools.screen import Screen
    from m3tools.screen.shell import auto_shell

    screen = Screen(auto_shell(args.backend))
    frame = screen.capture()
    out = args.out or os.path.join(STATE_DIR, "shot.png")
    os.makedirs(os.path.dirname(out) or ".", exist_ok=True)
    frame.save_png(out)
    print(f"{frame} -> {out}  (backend={screen.shell.name})")
    if args.at:
        for spec in args.at:
            x, y = (int(v) for v in spec.split(",", 1))
            print(f"  ({x},{y}) = rgb{frame.rgb(x, y)}")


def cmd_doctor(args) -> None:
    """Explain what this device can and cannot do before anything else fails."""
    from m3tools.farm.input import find_touch_device
    import shutil

    print(f"python      : {sys.version.split()[0]}")
    print(f"uid         : {os.getuid()} ({'root' if os.getuid() == 0 else 'root degil'})")
    print(f"su          : {shutil.which('su') or 'yok'}")
    print(f"adb         : {shutil.which('adb') or 'yok'}")
    try:
        from m3tools.screen.shell import auto_shell
        print(f"ekran backend: {auto_shell().name}")
    except Exception as e:
        print(f"ekran backend: yok ({e})")
    try:
        with open("/proc/sys/kernel/yama/ptrace_scope") as fh:
            print(f"ptrace_scope: {fh.read().strip()} (0 olmali)")
    except OSError:
        print("ptrace_scope: yok (Android'de normal)")
    dev = find_touch_device()
    if dev:
        print(f"dokunmatik  : {dev} ({'yazilabilir' if os.access(dev, os.W_OK) else 'yazilamaz - root gerekli'})")
    else:
        print("dokunmatik  : bulunamadi")
    print(f"durum dizini: {STATE_DIR}")
    if args.pid:
        pid = proc.resolve_pid(args.pid)
        ok, why = can_ptrace(pid)
        print(f"pid {pid}     : {'okunabilir' if ok else why}")


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(prog="m3", description=__doc__,
                                formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="cmd", required=True)

    s = sub.add_parser("ps", help="surecleri listele")
    s.add_argument("pattern", nargs="?", default="")
    s.set_defaults(func=cmd_ps)

    s = sub.add_parser("maps", help="bellek haritasi / yuklu moduller")
    s.add_argument("-p", "--pid", required=True)
    s.add_argument("--modules", action="store_true", help="modul yukleme adresleri")
    s.add_argument("--all", action="store_true", help="filtrelenmemis tum bolgeler")
    s.set_defaults(func=cmd_maps)

    s = sub.add_parser("scan", help="ilk tarama")
    s.add_argument("-p", "--pid", required=True)
    s.add_argument("-t", "--type", default="i32", choices=list(values.TYPES))
    s.add_argument("-v", "--value")
    s.add_argument("--unknown", action="store_true", help="deger bilinmiyor (anlik goruntu al)")
    s.add_argument("--align", type=int)
    s.add_argument("--stack", action="store_true", help="thread stack'lerini de tara")
    s.add_argument("--no-heap", action="store_true")
    s.add_argument("--no-anon", action="store_true")
    s.add_argument("--no-libs", action="store_true")
    s.add_argument("--show", type=int, default=20)
    s.set_defaults(func=cmd_scan)

    s = sub.add_parser("next", help="taramayi daralt")
    s.add_argument("-v", "--value")
    s.add_argument("--between", help="lo,hi araligi")
    s.add_argument("--op", choices=["changed", "unchanged", "increased", "decreased", "ne", "gt", "lt"])
    s.add_argument("--show", type=int, default=20)
    s.set_defaults(func=cmd_next)

    s = sub.add_parser("list", help="kalan adaylari goster")
    s.add_argument("--show", type=int, default=40)
    s.set_defaults(func=cmd_list)

    s = sub.add_parser("watch", help="adresi/adaylari canli izle")
    s.add_argument("-p", "--pid")
    s.add_argument("-a", "--address")
    s.add_argument("-t", "--type", default="i32", choices=list(values.TYPES))
    s.add_argument("-i", "--interval", type=float, default=0.5)
    s.add_argument("--show", type=int, default=20)
    s.set_defaults(func=cmd_watch)

    s = sub.add_parser("write", help="adrese deger yaz")
    s.add_argument("-p", "--pid", required=True)
    s.add_argument("-a", "--address", required=True)
    s.add_argument("-t", "--type", default="i32", choices=list(values.TYPES))
    s.add_argument("-v", "--value", required=True)
    s.set_defaults(func=cmd_write)

    s = sub.add_parser("pmap", help="pointer haritasini kur ve kaydet")
    s.add_argument("-p", "--pid", required=True)
    s.add_argument("--stack", action="store_true")
    s.set_defaults(func=cmd_pmap)

    s = sub.add_parser("pointer", help="adres icin kalici pointer zinciri bul")
    s.add_argument("address")
    s.add_argument("-p", "--pid")
    s.add_argument("--depth", type=int, default=5)
    s.add_argument("--max-offset", default="0x1000")
    s.add_argument("--max-results", type=int, default=20)
    s.add_argument("--rebuild", action="store_true", help="pointer haritasini yeniden kur")
    s.add_argument("--name", help="bulunan zinciri bu isimle tabloya kaydet")
    s.add_argument("--type", default="i32", choices=list(values.TYPES))
    s.add_argument("--pick", type=int, default=0, help="kaydedilecek zincirin indeksi")
    s.add_argument("--note")
    s.set_defaults(func=cmd_pointer)

    s = sub.add_parser("shot", help="ekran goruntusu al (koordinat kalibrasyonu)")
    s.add_argument("-o", "--out")
    s.add_argument("--backend", default="auto",
                   choices=["auto", "su", "rish", "adb", "local"])
    s.add_argument("--at", action="append", metavar="X,Y",
                   help="bu noktanin rengini de yaz")
    s.set_defaults(func=cmd_shot)

    s = sub.add_parser("farm", help="farm dongusunu calistir")
    s.add_argument("-c", "--config")
    s.add_argument("--table")
    s.add_argument("-n", "--dry-run", action="store_true",
                   help="dokunma gonderme, sadece ne yapacagini yaz")
    s.add_argument("-v", "--verbose", action="store_true")
    s.add_argument("--source", choices=["memory", "screen"],
                   help="config'teki kaynagi gecersiz kil")
    s.set_defaults(func=cmd_farm)

    s = sub.add_parser("probe", help="ekran problarini canli oku (kalibrasyon)")
    s.add_argument("-c", "--config")
    s.add_argument("-w", "--watch", action="store_true")
    s.add_argument("-i", "--interval", type=float, default=0.5)
    s.set_defaults(func=cmd_probe)

    s = sub.add_parser("doctor", help="cihaz/izin durumunu kontrol et")
    s.add_argument("-p", "--pid")
    s.set_defaults(func=cmd_doctor)

    s = sub.add_parser("table", help="offset tablosunu goster/duzenle")
    s.add_argument("-p", "--pid", help="verilirse degerleri canli coz")
    s.add_argument("--remove")
    s.set_defaults(func=cmd_table)

    return p


def main(argv=None) -> int:
    args = build_parser().parse_args(argv)
    try:
        args.func(args)
    except KeyboardInterrupt:
        print()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
