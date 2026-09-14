#!/usr/bin/env python3
"""Oyun neden acilmiyor? - anti-root / anti-emulator / lisans tanisi.

Oyun bir emulatorde acilmiyorsa uc olasilik var ve her birinin cozumu farkli.
Bu arac adb uzerinden misafir sistemi tarar, bir anti-cheat kontrolunun tam
olarak neyi gorecegini raporlar ve buldugu her ize gore somut bir cozum verir.

Kullanim:
    python3 tools/why_blocked.py                       # adb (varsayilan cihaz)
    python3 tools/why_blocked.py --serial 127.0.0.1:5555
    python3 tools/why_blocked.py --package com.hardmobile.client
"""
from __future__ import annotations

import argparse
import subprocess
import sys

ADB = "adb"


def sh(args, serial, cmd, timeout=20):
    argv = [ADB]
    if serial:
        argv += ["-s", serial]
    argv += ["exec-out", "sh", "-c", cmd]
    try:
        p = subprocess.run(argv, capture_output=True, timeout=timeout)
        return p.stdout.decode("utf-8", "replace").strip()
    except (OSError, subprocess.SubprocessError) as e:
        return f"__ERR__ {e}"


class Report:
    def __init__(self):
        self.findings = []   # (agirlik, baslik, ayrinti, cozum)

    def add(self, weight, title, detail, fix):
        self.findings.append((weight, title, detail, fix))

    def dump(self):
        order = {"YUKSEK": 0, "ORTA": 1, "DUSUK": 2, "BILGI": 3}
        self.findings.sort(key=lambda f: order.get(f[0], 9))
        for weight, title, detail, fix in self.findings:
            print(f"\n[{weight}] {title}")
            if detail:
                for line in detail.splitlines():
                    print(f"    {line}")
            if fix:
                print(f"  -> {fix}")


# --- su / root binary izleri ------------------------------------------------
SU_PATHS = [
    "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su",
    "/system/sbin/su", "/vendor/bin/su", "/data/local/bin/su",
    "/data/local/xbin/su", "/magisk/.core/bin/su",
]
ROOT_APPS = [
    "com.topjohnwu.magisk", "eu.chainfire.supersu", "com.noshufou.android.su",
    "com.koushikdutta.superuser", "com.thirdparty.superuser",
    "com.ramdroid.appquarantine", "me.weishu.kernelsu",
]
# Emulator parmak izleri: getprop degerlerinde bu dizeler gecerse tespit kolaydir.
EMU_PROP_HINTS = [
    ("ro.product.model", ["sdk", "emulator", "android sdk"]),
    ("ro.product.brand", ["generic", "android"]),
    ("ro.product.device", ["generic", "vbox", "goldfish", "ranchu"]),
    ("ro.hardware", ["goldfish", "ranchu", "vbox", "ttvm", "nox", "cancro"]),
    ("ro.kernel.qemu", ["1"]),
    ("ro.bootloader", ["unknown"]),
    ("ro.build.tags", ["test-keys"]),
]


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--serial")
    ap.add_argument("--package", default="com.hardmobile.client")
    args = ap.parse_args()
    S = args.serial
    r = Report()

    # 0) Baglanti ve yetki
    who = sh([], S, "id")
    if who.startswith("__ERR__"):
        print("adb'ye ulasilamadi. Emulator acik mi, 'adb connect' yapildi mi?")
        print(who)
        return 1
    root = "uid=0" in who
    print("=" * 68)
    print(f"  adb kimligi : {who}")
    print(f"  root        : {'EVET' if root else 'HAYIR'}")
    print("=" * 68)

    # 1) su binary izleri
    found_su = []
    for p in SU_PATHS:
        if sh([], S, f'[ -e "{p}" ] && echo VAR', 10) == "VAR":
            found_su.append(p)
    which_su = sh([], S, "command -v su 2>/dev/null || which su 2>/dev/null")
    if which_su and not which_su.startswith("__ERR__") and which_su:
        found_su.append(which_su + " (PATH)")
    if found_su:
        r.add("YUKSEK", "su binary'si gorunuyor",
              "\n".join(found_su),
              "Emulatorun 'Root izni' anahtarini KAPATIN ve yeniden baslatin. "
              "adbd zaten root oldugu icin araclar yine calisir. Kapatinca hala "
              "kaliyorsa Magisk DenyList gerekir (asagi bakin).")
    else:
        r.add("BILGI", "su binary'si bulunamadi",
              "Bilinen yollarda ve PATH'te su yok.",
              "Anti-root'un ilk kontrolu temiz.")

    # 2) root uygulamalari
    pkgs = sh([], S, "pm list packages 2>/dev/null")
    found_apps = [a for a in ROOT_APPS if a in pkgs]
    if found_apps:
        r.add("YUKSEK", "root yonetici uygulamasi kurulu",
              "\n".join(found_apps),
              "Superuser/Magisk uygulamasini kaldirin ya da adini gizleyin "
              "(Magisk'te 'Uygulamayi gizle' / random paket adi).")

    # 3) test-keys ve emulator parmak izleri
    emu_hits = []
    for prop, needles in EMU_PROP_HINTS:
        val = sh([], S, f"getprop {prop}").lower()
        if any(n in val for n in needles):
            emu_hits.append(f"{prop} = {val}")
    if emu_hits:
        r.add("ORTA", "emulator parmak izi",
              "\n".join(emu_hits),
              "Oyun emulator tespiti yapiyorsa bu degerleri gercek bir cihaza "
              "benzetmek gerekir. LDPlayer'da bazi surumler bunu ayardan "
              "'cihaz profili' ile yapar; kalicisi Magisk + 'MagiskHide Props "
              "Config' modulu ile prop degerlerini gercek bir telefona ayarlamak.")

    # 4) Zygisk / Magisk var mi (cozum icin bilmek gerekli)
    magisk = sh([], S, "magisk -V 2>/dev/null || echo yok")
    if magisk and magisk != "yok" and not magisk.startswith("__ERR__"):
        r.add("BILGI", f"Magisk kurulu (surum {magisk})",
              "", "Oyunu Magisk > DenyList'e ekleyin ve Zygisk'i acin.")
    else:
        r.add("BILGI", "Magisk yok",
              "", "Root gizleme gerekiyorsa emulatore Magisk kurmak gerekecek.")

    # 5) pairip lisans kontrolu
    if args.package in pkgs:
        # Oyunun kendi izinleri / bilesenleri pairip iceriyor mu?
        dump = sh([], S, f"pm dump {args.package} 2>/dev/null | grep -i pairip | head -5")
        # Son coken surec pairip miydi?
        crash = sh([], S,
                   f"logcat -d -t 300 2>/dev/null | grep -iE 'pairip|licensecheck|"
                   f"integrity' | tail -5")
        detail = []
        if dump and not dump.startswith("__ERR__"):
            detail.append("pairip bileseni: " + dump.replace("\n", " | "))
        if crash and not crash.startswith("__ERR__") and crash:
            detail.append("son loglar:\n" + crash)
        r.add("ORTA", "pairip lisans / butunluk kontrolu",
              "\n".join(detail) if detail else
              "Paket kurulu. pairip logu bulunamadi (iyi olabilir).",
              "pairip Play uzerinden kurulumu ve imza butunlugunu dogrular. "
              "Oyunu MUTLAKA emulatorun Play Store'undan kurun, APK'dan degil. "
              "Play Store'dan kuruluysa ve hala coyuyorsa emulator tespiti "
              "(yukaridaki ORTA madde) daha olasidir.")
    else:
        r.add("YUKSEK", f"{args.package} kurulu degil",
              "Paket listede yok.",
              "Oyunu emulatorun Play Store'undan kurun.")

    # 6) Son crash sebebi (genel)
    tomb = sh([], S,
              f"logcat -d -t 500 2>/dev/null | grep -iE '{args.package}.*"
              f"(FATAL|died|SIGABRT|SIGSEGV)|AndroidRuntime.*{args.package}' | tail -8")
    if tomb and not tomb.startswith("__ERR__") and tomb:
        r.add("ORTA", "oyun surecinde son hata kayitlari",
              tomb,
              "Yukaridaki satirlar coku sebebini gosterir: 'pairip' -> lisans, "
              "'su'/'root' -> root tespiti, 'SIGSEGV libX.so' -> arm ceviri "
              "uyumsuzlugu (emulatorde ARM cevirisini acin).")

    r.dump()
    print("\n" + "=" * 68)
    print("  Ozet karar")
    print("=" * 68)
    if found_su or found_apps:
        print("  En olasi sebep: ROOT TESPITI. Once emulatorun root anahtarini")
        print("  kapatip deneyin; kalirsa Magisk + DenyList.")
    elif emu_hits:
        print("  En olasi sebep: EMULATOR TESPITI. Cihaz profili / prop gizleme.")
    else:
        print("  su/emulator izi net degil. pairip logcat ciktisina bakin;")
        print("  oyunu Play Store'dan kurdugunuzdan emin olun.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
