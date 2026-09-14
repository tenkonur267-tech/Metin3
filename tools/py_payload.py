#!/usr/bin/env python3
"""Gomulu Python yuku analizi.

Bazi Android oyunlari (Metin2 turevleri dahil) oyun mantigini gomulu bir
Python yorumlayicisinda calistirir. O durumda bellek taramasi / offset avi
gereksizdir: mantik kaynak ya da bytecode olarak APK'nin icindedir.

Bu arac APK icindeki Python yukunu bulur, standart kutuphaneyi oyunun kendi
kodundan ayirir, .pyc dosyalarinin surumunu magic number'dan tespit eder ve
oyuna ait modulleri listeler.

Kullanim:
    python3 tools/py_payload.py input/apk/HardMobile.apk
    python3 tools/py_payload.py input/apk/HardMobile.apk --extract out/py
"""
from __future__ import annotations

import argparse
import os
import posixpath
import re
import zipfile
from collections import defaultdict

# CPython bytecode magic -> surum. Kaynak: CPython Lib/importlib/_bootstrap_external.py
PYC_MAGIC = {
    62211: "2.7", 3190: "3.2", 3230: "3.3", 3260: "3.4", 3310: "3.5",
    3379: "3.6", 3394: "3.7", 3413: "3.8", 3425: "3.9", 3439: "3.10",
    3495: "3.11", 3531: "3.12", 3571: "3.13",
}

# Standart kutuphane oldugu belli olan dizin adlari.
STDLIB_MARKERS = (
    "python_stdlib", "/encodings/", "/lib2to3/", "/unittest/", "/email/",
    "/json/", "/xml/", "/importlib/", "/collections/", "/ctypes/",
    "/distutils/", "/logging/", "/sqlite3/", "/asyncio/",
)

# Bu isimler saf stdlib modulleridir; oyun koduyla karistirilmasin.
STDLIB_TOPLEVEL = {
    "abc", "codecs", "locale", "os", "re", "types", "warnings", "io", "stat",
    "site", "sre_compile", "sre_constants", "sre_parse", "functools",
    "operator", "keyword", "heapq", "itertools", "reprlib", "weakref",
    "copyreg", "traceback", "linecache", "tokenize", "token", "posixpath",
    "genericpath", "fnmatch", "enum", "struct", "copy", "pickle", "base64",
    "hashlib", "random", "bisect", "datetime", "calendar", "threading",
    "socket", "selectors", "ssl", "http", "urllib", "zipfile", "shutil",
    "tempfile", "subprocess", "signal", "contextlib", "inspect", "dis",
    "opcode", "typing", "string", "textwrap", "argparse", "gettext",
    "encodings", "sysconfig", "platform",
}

# Metin2 istemcisinin bilinen Python modulleri - varsa oyunun kaynagi acikta.
METIN2_HINTS = (
    "uiCharacter", "uiInventory", "uiPhaseCurtain", "uiTaskBar", "uiChat",
    "game", "intrologin", "introloading", "introselect", "prototype",
    "systemSetting", "playersettingmodule", "networkModule", "constInfo",
    "chrmgr", "ui", "uiScriptLocale", "localeInfo", "emotion", "mouseModule",
    "exception", "musicInfo", "serverInfo", "app", "net", "chr", "player",
)


def classify(name: str) -> str:
    low = name.lower()
    if any(m in low for m in STDLIB_MARKERS):
        return "stdlib"
    base = posixpath.basename(name)
    stem = base.split(".")[0]
    if stem in STDLIB_TOPLEVEL:
        return "stdlib"
    return "app"


def pyc_version(data: bytes) -> str:
    if len(data) < 4:
        return "?"
    magic = int.from_bytes(data[:2], "little")
    return PYC_MAGIC.get(magic, f"bilinmeyen (magic {magic})")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("apk")
    ap.add_argument("--extract", metavar="DIZIN",
                    help="oyuna ait Python dosyalarini bu dizine cikar")
    ap.add_argument("--limit", type=int, default=120)
    args = ap.parse_args()

    with zipfile.ZipFile(args.apk) as z:
        infos = z.infolist()
        py = [i for i in infos
              if i.filename.endswith((".py", ".pyc", ".pyo", ".pyd", ".egg"))]
        if not py:
            print("APK icinde Python dosyasi yok.")
            return 1

        groups: dict[str, list] = defaultdict(list)
        for i in py:
            groups[classify(i.filename)].append(i)

        app = sorted(groups["app"], key=lambda i: i.filename)
        std = groups["stdlib"]

        print("=" * 68)
        print(f"  Python dosyasi : {len(py)} "
              f"({len(app)} oyun kodu, {len(std)} standart kutuphane)")
        src = sum(1 for i in py if i.filename.endswith(".py"))
        byc = len(py) - src
        print(f"  Bicim          : {src} kaynak (.py), {byc} bytecode (.pyc)")

        # Surum tespiti: ilk .pyc'nin magic'i
        for i in py:
            if i.filename.endswith((".pyc", ".pyo")):
                print(f"  Python surumu  : {pyc_version(z.read(i)[:4])} "
                      f"({i.filename})")
                break

        # Metin2 modul ipuclari
        names = {posixpath.basename(i.filename).split(".")[0] for i in py}
        found = sorted(names & set(METIN2_HINTS))
        print("=" * 68)
        if found:
            print(f"\n[Metin2 istemci modulleri bulundu] {len(found)} adet")
            print("  " + ", ".join(found))
            print("\n  Oyun mantigi kaynak/bytecode olarak elinizde. Offset")
            print("  aramaya gerek yok - dogrudan okunabilir.")

        # Oyun koduna ait dizinler
        dirs: dict[str, int] = defaultdict(int)
        for i in app:
            dirs[posixpath.dirname(i.filename)] += 1
        print(f"\n[oyun koduna ait dizinler] {len(dirs)} adet")
        for d, n in sorted(dirs.items(), key=lambda kv: -kv[1]):
            print(f"  {n:>5} dosya  {d or '(kok)'}")

        print(f"\n[oyun kodu dosyalari] ilk {min(args.limit, len(app))}")
        for i in app[:args.limit]:
            print(f"  {i.file_size:>8}  {i.filename}")
        if len(app) > args.limit:
            print(f"  ... ve {len(app) - args.limit} dosya daha")

        # Python disi ama ilginc varliklar
        other = [i for i in infos if i.filename.startswith("assets/")
                 and not i.filename.endswith((".py", ".pyc", ".pyo"))]
        interesting = [i for i in other
                       if re.search(r"\.(json|xml|txt|cfg|ini|dat|epk|eix|lua|js)$",
                                    i.filename, re.I)]
        if interesting:
            print(f"\n[diger ilginc varliklar] {len(interesting)} adet")
            for i in sorted(interesting, key=lambda i: -i.file_size)[:25]:
                print(f"  {i.file_size:>8}  {i.filename}")

        if args.extract:
            os.makedirs(args.extract, exist_ok=True)
            for i in app:
                z.extract(i, args.extract)
            print(f"\n-> {len(app)} dosya {args.extract} altina cikarildi")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
