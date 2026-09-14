#!/usr/bin/env python3
"""Oyunun indirilmis verisini erisilebilir depolamada bul.

Metin2 turevi istemciler betiklerini ve varliklarini .epk/.eix paketlerinden
yukler; bunlar APK'da degil, ilk acilista indirilen veri dizinindedir. Root
olmadan bu dizine erisilebiliyorsa oyunun kendi Python betikleri duzenlenebilir
ve otomasyon istemcinin icine konabilir - bellek erisimi gerekmez.

Bu arac erisilebilir depolamayi tarar, oyuna ait gorunen dosyalari
siniflandirir ve her birinin YAZILABILIR olup olmadigini soyler; cunku
stratejiyi belirleyen sey okuma degil, yazma iznidir.

Kullanim:
    python3 tools/find_gamedata.py
    python3 tools/find_gamedata.py --package com.hardmobile.client
"""
from __future__ import annotations

import argparse
import os

# Metin2 turevi istemcilerin varlik/paket uzantilari ve dikkat cekici adlar.
PACK_EXT = (".epk", ".eix", ".eter", ".pack", ".dat", ".zip", ".obb")
SCRIPT_EXT = (".py", ".pyc", ".txt", ".json", ".cfg", ".ini", ".lua")
INTERESTING_NAMES = (
    "root", "uiscript", "locale", "pack", "script", "game", "metin",
    "hard", "client", "data",
)


def human(n: int) -> str:
    for unit in ("B", "KB", "MB", "GB"):
        if n < 1024:
            return f"{n:.1f}{unit}"
        n /= 1024
    return f"{n:.1f}TB"


def candidate_roots(package: str | None) -> list[str]:
    home = os.path.expanduser("~")
    roots = [
        f"{home}/storage/shared",
        f"{home}/storage/external-1",
        "/storage/emulated/0",
        "/sdcard",
    ]
    if package:
        for base in list(roots):
            roots.insert(0, f"{base}/Android/data/{package}")
            roots.insert(0, f"{base}/Android/obb/{package}")
    seen, out = set(), []
    for r in roots:
        real = os.path.realpath(r)
        if real not in seen and os.path.isdir(r):
            seen.add(real)
            out.append(r)
    return out


def writable(path: str) -> bool:
    return os.access(path, os.W_OK)


def scan(root: str, package: str | None, max_depth: int = 6):
    """Ilginc dosyalari topla; derinlik sinirli, izin hatalarina toleransli."""
    hits = []
    base_depth = root.rstrip("/").count("/")
    for dirpath, dirnames, filenames in os.walk(root, onerror=lambda e: None):
        if dirpath.count("/") - base_depth >= max_depth:
            dirnames[:] = []
            continue
        # Medya klasorlerini ele - buyuk ve alakasiz.
        dirnames[:] = [d for d in dirnames
                       if d not in ("DCIM", "Pictures", "Movies", "Music",
                                    "WhatsApp", "Telegram")]
        low_dir = dirpath.lower()
        dir_interesting = any(n in low_dir for n in INTERESTING_NAMES)
        if package and package.lower() in low_dir:
            dir_interesting = True
        for fn in filenames:
            low = fn.lower()
            if low.endswith(PACK_EXT) or (dir_interesting and low.endswith(SCRIPT_EXT)):
                full = os.path.join(dirpath, fn)
                try:
                    size = os.path.getsize(full)
                except OSError:
                    continue
                hits.append((full, size))
    return hits


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--package", default="com.hardmobile.client")
    ap.add_argument("--limit", type=int, default=80)
    ap.add_argument("--root", action="append", default=[],
                    help="ek arama koku (test veya ozel kurulum icin)")
    args = ap.parse_args()

    roots = [r for r in args.root if os.path.isdir(r)]
    roots += candidate_roots(args.package)
    print("=" * 68)
    print("  Erisilebilir kokler")
    print("=" * 68)
    for r in roots:
        try:
            entries = len(os.listdir(r))
            note = f"{entries} giris"
        except OSError as e:
            note = f"okunamiyor ({e.strerror})"
        print(f"  {'YAZILABILIR' if writable(r) else 'salt okunur ':<12} {r}  [{note}]")

    # Android/data erisimi Android 11+ ile kisitlandi; durumu acikca soyle.
    for base in (f"{os.path.expanduser('~')}/storage/shared", "/storage/emulated/0"):
        d = f"{base}/Android/data/{args.package}"
        if os.path.isdir(base + "/Android/data") and not os.path.isdir(d):
            print(f"\n  NOT: {d} gorunmuyor.")
            print("  Android 11+ uygulamalarin Android/data erisimini kisitlar.")
            print("  Oyun verisi dahili depolamada (/data/data/...) ise root")
            print("  olmadan erisilemez.")
            break

    print("\n" + "=" * 68)
    print("  Bulunan dosyalar")
    print("=" * 68)
    all_hits = []
    for r in roots:
        all_hits.extend(scan(r, args.package))
    # Ayni dosyaya birden fazla yoldan ulasilabilir (symlink'li kokler).
    unique = {os.path.realpath(p): (p, s) for p, s in all_hits}
    hits = sorted(unique.values(), key=lambda t: -t[1])
    if not hits:
        print("  Oyuna ait dosya bulunamadi.")
        print("  Veri muhtemelen /data/data/<paket>/ altinda - root gerekir.")
        return 0
    for path, size in hits[:args.limit]:
        flag = "YAZILABILIR" if writable(path) else "salt okunur"
        print(f"  {human(size):>9}  {flag:<12} {path}")
    if len(hits) > args.limit:
        print(f"  ... ve {len(hits) - args.limit} dosya daha")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
