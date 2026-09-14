#!/usr/bin/env python3
"""Root oncesi cihaz hazirlik raporu.

Root'lama cihaza ozgudur: uretici, model, Android surumu ve bootloader
durumu hangi yontemin gerektigini ve hangi risklerin gecerli oldugunu
belirler. Bu arac Termux'tan okunabilen ozellikleri toplayip tek bir rapora
cevirir; tahmin yerine cihazin kendi beyanini kullanir.

Kullanim:
    python3 tools/root_readiness.py
"""
from __future__ import annotations

import os
import shutil
import subprocess

PROPS = [
    ("ro.product.manufacturer", "Uretici"),
    ("ro.product.brand", "Marka"),
    ("ro.product.model", "Model"),
    ("ro.product.device", "Cihaz kodu"),
    ("ro.product.name", "Urun adi"),
    ("ro.build.version.release", "Android surumu"),
    ("ro.build.version.sdk", "API seviyesi"),
    ("ro.build.version.security_patch", "Guvenlik yamasi"),
    ("ro.build.fingerprint", "Build parmak izi"),
    ("ro.build.type", "Build tipi"),
    ("ro.boot.slot_suffix", "A/B slot"),
    ("ro.boot.dynamic_partitions", "Dinamik bolumler"),
    ("ro.virtual_ab.enabled", "Sanal A/B"),
]

# Bootloader kilidiyle ilgili ozellikler; ureticiye gore farkli isimler.
LOCK_PROPS = [
    "ro.boot.flash.locked",
    "ro.boot.verifiedbootstate",
    "ro.boot.vbmeta.device_state",
    "ro.oem_unlock_supported",
    "sys.oem_unlock_allowed",
    "ro.boot.veritymode",
]


def getprop(name: str) -> str:
    try:
        out = subprocess.run(["getprop", name], capture_output=True, timeout=10)
        return out.stdout.decode("utf-8", "replace").strip()
    except (OSError, subprocess.SubprocessError):
        return ""


def is_rooted() -> tuple[bool, str]:
    su = shutil.which("su")
    if not su:
        return False, "su binary'si yok"
    try:
        out = subprocess.run(["su", "-c", "id"], capture_output=True, timeout=10)
    except (OSError, subprocess.SubprocessError) as e:
        return False, f"su calistirilamadi ({e})"
    text = out.stdout.decode("utf-8", "replace")
    if "uid=0" in text:
        return True, text.strip()
    return False, "su var ama uid=0 vermiyor (Termux'un tsu sarmalayicisi olabilir)"


def main() -> int:
    print("=" * 68)
    print("  Cihaz")
    print("=" * 68)
    props = {}
    for key, label in PROPS:
        val = getprop(key)
        props[key] = val
        if val:
            print(f"  {label:<20}: {val}")

    print("\n" + "=" * 68)
    print("  Bootloader / dogrulanmis onyukleme")
    print("=" * 68)
    lock = {}
    for key in LOCK_PROPS:
        val = getprop(key)
        lock[key] = val
        if val:
            print(f"  {key:<32}: {val}")
    if not any(lock.values()):
        print("  Bu ozelliklerin hicbiri okunamadi.")

    print("\n" + "=" * 68)
    print("  Root durumu")
    print("=" * 68)
    rooted, detail = is_rooted()
    print(f"  uid          : {os.getuid()}")
    print(f"  root         : {'EVET' if rooted else 'HAYIR'} - {detail}")

    print("\n" + "=" * 68)
    print("  Degerlendirme")
    print("=" * 68)
    if rooted:
        print("  Cihaz zaten root'lu. 'm3 doctor' ile devam edin.")
        return 0

    locked = lock.get("ro.boot.flash.locked")
    state = lock.get("ro.boot.verifiedbootstate")
    if locked == "1" or state == "green":
        print("  * Bootloader KILITLI. Root icin once kilidi acmak gerekir ve")
        print("    kilit acma islemi cihazi FABRIKA AYARLARINA DONDURUR -")
        print("    telefondaki tum veri silinir. Once yedek alin.")
    elif locked == "0" or state in ("orange", "yellow"):
        print("  * Bootloader ACIK gorunuyor. Magisk ile yamalanmis bir boot")
        print("    imaji flash'lamak yeterli olabilir.")
    else:
        print("  * Bootloader durumu okunamadi. Cihazi fastboot moduna alip")
        print("    'fastboot oem device-info' ile bir PC'den dogrulayin.")

    brand = (props.get("ro.product.brand") or "").lower()
    if brand in ("xiaomi", "redmi", "poco"):
        print("  * Xiaomi/Redmi/Poco: kilit acma Mi Unlock hesabi ve genelde")
        print("    7 gunluk bekleme suresi ister.")
    elif brand in ("samsung",):
        print("  * Samsung: kilit acma Knox sigortasini kalici olarak atar;")
        print("    Samsung Pay, Secure Folder ve bazi kurumsal uygulamalar")
        print("    bir daha calismaz. ABD modellerinde kilit acilamaz.")
    elif brand in ("oneplus", "google", "nothing", "motorola", "asus"):
        print(f"  * {brand.title()}: kilit acma genelde desteklenir.")
    elif brand in ("huawei", "honor"):
        print("  * Huawei/Honor: resmi kilit acma 2018'den beri kapali.")

    sdk = props.get("ro.build.version.sdk", "")
    if sdk.isdigit() and int(sdk) >= 30:
        print(f"  * Android API {sdk}: A/B ve dinamik bolumler kullaniliyor")
        print("    olabilir; Magisk'i init_boot ya da boot imajina yamalamak")
        print("    cihaza gore degisir, yanlis secim onyukleme dongusune yol acar.")

    print("\n  Gerekenler: bir PC, USB kablosu, cihazin KENDI surumune ait")
    print("  stok firmware (boot/init_boot imaji icin) ve Magisk uygulamasi.")
    print("  Bu build parmak izini not edin, indirilecek firmware onunla")
    print("  ayni olmali:")
    print(f"    {props.get('ro.build.fingerprint', '(okunamadi)')}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
