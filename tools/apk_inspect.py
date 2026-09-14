#!/usr/bin/env python3
"""APK yapisal analizi - motor imzasi bulunamadiginda ne oldugunu soyler.

engine_detect.py bilinen oyun motorlarinin imzalarina bakar. Bir APK'da hic
native kutuphane yoksa o liste bos doner ve geriye "bilinmiyor" kalir. Bu arac
bir adim geri gider: APK'nin icinde gercekte ne oldugunu -- en buyuk dosyalar,
ust duzey klasorler, framework izleri, indirilen icerik isaretleri -- dokerek
hangi otomasyon stratejisinin uygulanabilir oldugunu belirler.

Kullanim:
    python3 tools/apk_inspect.py input/apk/HardMobile.apk
"""
from __future__ import annotations

import os
import re
import sys
import zipfile
from collections import defaultdict

# (etiket, aciklama, yol kaliplari, otomasyon stratejisi)
FRAMEWORKS = [
    ("flutter", "Flutter",
     [r"^lib/[^/]+/libflutter\.so$", r"^assets/flutter_assets/"],
     "Dart AOT; UI agaci erisilmez, ekran tabanli otomasyon gerekir"),
    ("react-native", "React Native",
     [r"^assets/index\.android\.bundle$", r"^lib/[^/]+/libreactnativejni\.so$"],
     "JS bundle okunabilir - oyun mantigi bundle icinde acik olabilir"),
    ("cordova", "Cordova / PhoneGap",
     [r"^assets/www/index\.html$", r"^assets/www/cordova\.js$"],
     "WebView; tum oyun mantigi assets/www icinde JS olarak okunabilir"),
    ("capacitor", "Capacitor",
     [r"^assets/capacitor\.config\.json$", r"^assets/public/index\.html$"],
     "WebView; oyun mantigi assets/public icinde JS olarak okunabilir"),
    ("webview-generic", "WebView tabanli (jenerik)",
     [r"^assets/.*\.html$"],
     "WebView; HTML/JS varliklari incelenebilir"),
    ("xamarin", "Xamarin / .NET",
     [r"^assemblies/", r"^lib/[^/]+/libmonodroid\.so$"],
     ".NET assembly'leri dogrudan decompile edilebilir"),
    ("godot", "Godot",
     [r"^assets/.*\.pck$", r"^lib/[^/]+/libgodot_android\.so$"],
     "PCK acilabilir, GDScript cikarilabilir"),
    ("defold", "Defold",
     [r"^lib/[^/]+/libdefold\.so$"], "native-scan"),
    ("gamemaker", "GameMaker Studio",
     [r"^lib/[^/]+/libyoyo\.so$", r"^assets/game\.droid$"], "native-scan"),
    ("solar2d", "Solar2D / Corona",
     [r"^lib/[^/]+/libcorona\.so$", r"^assets/resource\.car$"],
     "Lua bytecode cikarilabilir"),
    ("libgdx", "libGDX",
     [r"^lib/[^/]+/libgdx\.so$"], "Java; dex decompile edilebilir"),
    ("unity-il2cpp", "Unity (IL2CPP)",
     [r"^assets/bin/Data/Managed/Metadata/global-metadata\.dat$",
      r"^lib/[^/]+/libil2cpp\.so$"],
     "global-metadata.dat'tan alan offsetleri dogrudan cikar"),
    ("unity-mono", "Unity (Mono)",
     [r"^assets/bin/Data/Managed/Assembly-CSharp\.dll$",
      r"^lib/[^/]+/libmono.*\.so$"],
     "Assembly-CSharp.dll decompile edilebilir"),
    ("unreal", "Unreal Engine",
     [r"^lib/[^/]+/libUE4\.so$", r"^lib/[^/]+/libUnreal\.so$"], "gnames-gobjects"),
    ("cocos2d", "Cocos2d-x",
     [r"^lib/[^/]+/libcocos2d.*\.so$", r"^assets/src/main\.lua$"],
     "Lua/JS script'leri assets icinde olabilir"),
]

# Calisma aninda indirilen icerik isaretleri: APK kucukse oyun varliklari
# sonradan geliyordur ve asil motor APK'da gorunmez.
DOWNLOADER_HINTS = [
    (r"libunity\.so", "Unity runtime"),
    (r"AssetBundle", "Unity AssetBundle"),
    (r"obb", "OBB genisleme dosyasi"),
    (r"libmain\.so", "jenerik native launcher"),
]


def human(n: int) -> str:
    for unit in ("B", "KB", "MB", "GB"):
        if n < 1024:
            return f"{n:.1f}{unit}"
        n /= 1024
    return f"{n:.1f}TB"


def manifest_strings(data: bytes) -> list[str]:
    """AndroidManifest.xml binary; okunabilir UTF-16 dizgileri kabaca cikar."""
    out = []
    for m in re.finditer(rb"(?:[\x20-\x7e]\x00){4,}", data):
        s = m.group().decode("utf-16-le", "ignore").strip()
        if s:
            out.append(s)
    return out


def inspect(path: str) -> None:
    with zipfile.ZipFile(path) as z:
        infos = z.infolist()
        names = [i.filename for i in infos]

        total = sum(i.file_size for i in infos)
        print("=" * 68)
        print(f"  DOSYA      : {os.path.basename(path)}")
        print(f"  GIRIS      : {len(infos)} dosya, acilmis {human(total)}")
        print("=" * 68)

        # -- framework tespiti
        hits = []
        for key, label, patterns, strategy in FRAMEWORKS:
            matched = [n for n in names
                       if any(re.match(p, n) for p in patterns)]
            if matched:
                hits.append((label, strategy, matched[:4]))
        print("\n[framework tespiti]")
        if hits:
            for label, strategy, matched in hits:
                print(f"  * {label}")
                print(f"    strateji: {strategy}")
                for m in matched:
                    print(f"      - {m}")
        else:
            print("  Bilinen framework imzasi yok.")

        # -- native kutuphaneler
        libs = [i for i in infos if i.filename.startswith("lib/")]
        print(f"\n[native kutuphaneler] {len(libs)} adet")
        for i in sorted(libs, key=lambda i: -i.file_size)[:20]:
            print(f"  {human(i.file_size):>10}  {i.filename}")
        if not libs:
            print("  YOK - APK icinde hicbir .so dosyasi bulunmuyor.")
            print("  Bu genelde su anlama gelir: istemci saf Java/Kotlin, ya")
            print("  WebView tabanli, ya da native motor ilk acilista indiriliyor.")

        # -- ust duzey klasorler
        print("\n[ust duzey klasorler]")
        tops: dict[str, list[int]] = defaultdict(lambda: [0, 0])
        for i in infos:
            top = i.filename.split("/")[0] if "/" in i.filename else "(kok)"
            tops[top][0] += 1
            tops[top][1] += i.file_size
        for top, (count, size) in sorted(tops.items(), key=lambda kv: -kv[1][1]):
            print(f"  {human(size):>10}  {count:>5} dosya  {top}")

        # -- en buyuk dosyalar
        print("\n[en buyuk 20 dosya]")
        for i in sorted(infos, key=lambda i: -i.file_size)[:20]:
            print(f"  {human(i.file_size):>10}  {i.filename}")

        # -- dex
        dex = [i for i in infos if i.filename.endswith(".dex")]
        if dex:
            print(f"\n[dex] {len(dex)} dosya, toplam "
                  f"{human(sum(i.file_size for i in dex))}")
            print("  Java/Kotlin kodu burada. jadx ile decompile edilebilir:")
            print("    pkg install openjdk-17 && jadx -d out/src input/apk/*.apk")

        # -- indirilen icerik isaretleri
        joined = "\n".join(names)
        marks = [desc for pat, desc in DOWNLOADER_HINTS
                 if re.search(pat, joined, re.I)]
        if marks:
            print("\n[indirilen icerik isaretleri]")
            for m in marks:
                print(f"  * {m}")

        # -- manifest
        try:
            data = z.read("AndroidManifest.xml")
        except KeyError:
            return
        strings = manifest_strings(data)
        activities = [s for s in strings if "." in s and
                      re.search(r"(Activity|Unity|Cordova|Flutter|RN|MainApp)", s)]
        perms = [s for s in strings if s.startswith("android.permission.")]
        print("\n[manifest - aktiviteler]")
        for s in dict.fromkeys(activities):
            print(f"  {s}")
        print("\n[manifest - izinler]")
        for s in dict.fromkeys(perms):
            print(f"  {s}")


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__)
        return 2
    path = sys.argv[1]
    if os.path.isdir(path):
        apks = [os.path.join(path, f) for f in sorted(os.listdir(path))
                if f.lower().endswith((".apk", ".apks", ".xapk", ".apkm"))]
        if not apks:
            print(f"{path} icinde apk yok")
            return 1
        for a in apks:
            inspect(a)
        return 0
    inspect(path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
