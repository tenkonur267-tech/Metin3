#!/usr/bin/env python3
"""
Metin3 - oyun motoru tespiti.

Kullanim:
    python3 tools/engine_detect.py input/apk/base.apk
    python3 tools/engine_detect.py input/apk/           # split apk klasoru
    python3 tools/engine_detect.py /cikarilmis/klasor/  # zaten unzip edilmis

Motoru bilmek kritik: IL2CPP ise offsetleri TAHMIN ETMEYE GEREK YOK,
runtime API'si (il2cpp_field_get_offset) birebir veriyor. Native ise
klasik pointer-scan / disassembly yolundan gidilir.
"""
from __future__ import annotations

import json
import os
import sys
import zipfile
from dataclasses import dataclass, field, asdict


# (isim, esles kaliplari, aciklama, offset stratejisi)
SIGNATURES = [
    (
        "unity-il2cpp",
        ["assets/bin/Data/Managed/Metadata/global-metadata.dat", "lib/*/libil2cpp.so"],
        "Unity (IL2CPP derlemesi)",
        "il2cpp-api",
    ),
    (
        "unity-mono",
        ["assets/bin/Data/Managed/Assembly-CSharp.dll", "lib/*/libmonobdwgc-2.0.so",
         "lib/*/libmono.so"],
        "Unity (Mono derlemesi)",
        "dotnet-reflection",
    ),
    (
        "unreal",
        ["lib/*/libUE4.so", "lib/*/libUnreal.so", "assets/*.pak"],
        "Unreal Engine",
        "gnames-gobjects",
    ),
    (
        "godot",
        ["lib/*/libgodot_android.so", "assets/*.pck"],
        "Godot Engine",
        "native-scan",
    ),
    (
        "cocos2d",
        ["lib/*/libcocos2djs.so", "lib/*/libcocos2dcpp.so"],
        "Cocos2d-x",
        "native-scan",
    ),
]

# Metin2/Metin3 turevlerinde sik gorulen native kutuphaneler
INTERESTING_LIBS = [
    "libil2cpp.so", "libunity.so", "libmain.so", "libgame.so",
    "libmetin.so", "libclient.so", "libnative-lib.so", "libcore.so",
]


def _match(pattern: str, names: list[str]) -> list[str]:
    """lib/*/libfoo.so gibi tek seviyeli glob eslesmesi."""
    if "*" not in pattern:
        return [n for n in names if n == pattern]
    import fnmatch
    return [n for n in names if fnmatch.fnmatch(n, pattern)]


@dataclass
class Report:
    source: str = ""
    engine: str = "bilinmiyor"
    engine_label: str = "Tespit edilemedi"
    strategy: str = "native-scan"
    matched: list[str] = field(default_factory=list)
    abis: list[str] = field(default_factory=list)
    native_libs: list[dict] = field(default_factory=list)
    metadata_version: int | None = None
    notes: list[str] = field(default_factory=list)


def list_entries(path: str) -> tuple[list[str], callable]:
    """(dosya listesi, okuyucu) dondurur. APK veya klasor kabul eder."""
    if os.path.isfile(path) and zipfile.is_zipfile(path):
        zf = zipfile.ZipFile(path)
        return zf.namelist(), lambda n, size=None: zf.open(n).read(size) if size else zf.open(n).read()

    if os.path.isdir(path):
        # klasorde apk varsa hepsini birlestir
        apks = [os.path.join(path, f) for f in sorted(os.listdir(path))
                if f.lower().endswith((".apk", ".apks", ".zip"))]
        if apks:
            names: list[str] = []
            owner: dict[str, str] = {}
            for a in apks:
                if not zipfile.is_zipfile(a):
                    continue
                with zipfile.ZipFile(a) as zf:
                    for n in zf.namelist():
                        if n not in owner:
                            owner[n] = a
                            names.append(n)

            def read(n, size=None):
                with zipfile.ZipFile(owner[n]) as zf:
                    with zf.open(n) as fh:
                        return fh.read(size) if size else fh.read()

            return names, read

        # duz cikarilmis klasor
        names = []
        for root, _dirs, files in os.walk(path):
            for f in files:
                full = os.path.join(root, f)
                names.append(os.path.relpath(full, path).replace(os.sep, "/"))

        def read_fs(n, size=None):
            with open(os.path.join(path, n), "rb") as fh:
                return fh.read(size) if size else fh.read()

        return names, read_fs

    raise SystemExit(f"[!] Okunamadi (apk/zip/klasor degil): {path}")


def detect(path: str) -> Report:
    names, read = list_entries(path)
    rep = Report(source=path)

    if not names:
        rep.notes.append("Kaynak bos - icinde hic dosya yok.")
        return rep

    # --- motor imzalari ---
    for engine, patterns, label, strategy in SIGNATURES:
        hits: list[str] = []
        for p in patterns:
            hits += _match(p, names)
        if hits:
            rep.engine = engine
            rep.engine_label = label
            rep.strategy = strategy
            rep.matched = sorted(set(hits))
            break

    # --- ABI'ler ---
    abis = set()
    for n in names:
        parts = n.split("/")
        if len(parts) >= 3 and parts[0] == "lib":
            abis.add(parts[1])
    rep.abis = sorted(abis)

    # --- native kutuphaneler ---
    for n in names:
        if not n.startswith("lib/") or not n.endswith(".so"):
            continue
        base = n.rsplit("/", 1)[-1]
        try:
            size = len(read(n))
        except Exception:
            size = -1
        rep.native_libs.append({
            "path": n,
            "name": base,
            "size": size,
            "interesting": base in INTERESTING_LIBS,
        })
    rep.native_libs.sort(key=lambda d: -d["size"])

    # --- global-metadata.dat surumu ---
    meta = [n for n in names if n.endswith("global-metadata.dat")]
    if meta:
        try:
            head = read(meta[0], 8)
            if head[:4] == b"\xaf\x1b\xb1\xfa":
                rep.metadata_version = int.from_bytes(head[4:8], "little")
                rep.notes.append(
                    f"global-metadata.dat gecerli, metadata surumu {rep.metadata_version}. "
                    "Sifrelenmemis - Il2CppDumper dogrudan calisir."
                )
            else:
                rep.notes.append(
                    f"global-metadata.dat sihirli sayisi beklenmedik ({head[:4].hex()}). "
                    "Muhtemelen SIFRELI/korumali - once unpack gerekir."
                )
        except Exception as exc:
            rep.notes.append(f"global-metadata.dat okunamadi: {exc}")

    # --- koruma / packer izleri ---
    packers = {
        "libjiagu": "360 Jiagu packer",
        "libDexHelper": "DexProtector",
        "libexec.so": "Bangcle/SecNeo",
        "libSecShell": "Bangcle SecShell",
        "libnesec": "NetEase packer",
        "libtprt": "Tencent Legu",
        "libshella": "SecNeo.A",
        "libmobisec": "Alibaba packer",
    }
    for n in names:
        for key, label in packers.items():
            if key in n:
                rep.notes.append(f"KORUMA tespit edildi: {label} ({n})")

    if rep.engine == "bilinmiyor":
        rep.notes.append(
            "Bilinen motor imzasi yok. Muhtemelen ozel/native bir istemci - "
            "strateji: readelf ile sembol tablosu + Frida ile native hook."
        )
    return rep


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 1
    rep = detect(sys.argv[1])

    print("=" * 68)
    print(f"  MOTOR      : {rep.engine_label}  [{rep.engine}]")
    print(f"  STRATEJI   : {rep.strategy}")
    print(f"  ABI        : {', '.join(rep.abis) or '-'}")
    if rep.metadata_version:
        print(f"  METADATA   : v{rep.metadata_version}")
    print("=" * 68)

    if rep.matched:
        print("\n[eslesen imzalar]")
        for m in rep.matched:
            print(f"  - {m}")

    if rep.native_libs:
        print("\n[native kutuphaneler / buyukten kucuge]")
        for lib in rep.native_libs[:15]:
            mark = " <== HEDEF" if lib["interesting"] else ""
            mb = lib["size"] / 1048576 if lib["size"] > 0 else 0
            print(f"  {mb:8.2f} MB  {lib['path']}{mark}")

    if rep.notes:
        print("\n[notlar]")
        for n in rep.notes:
            print(f"  * {n}")

    os.makedirs("out", exist_ok=True)
    with open("out/engine_report.json", "w", encoding="utf-8") as fh:
        json.dump(asdict(rep), fh, indent=2, ensure_ascii=False)
    print("\n-> out/engine_report.json yazildi")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
