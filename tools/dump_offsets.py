#!/usr/bin/env python3
"""
Frida uzerinden IL2CPP offsetlerini cikarip out/ altina yazar.

Onkosul:
    pip install frida-tools
    telefonda root + frida-server calisir durumda (bkz. docs/01-kurulum.md)

Kullanim:
    python3 tools/dump_offsets.py --package com.sirket.metin3 --all
    python3 tools/dump_offsets.py --package com.sirket.metin3 --find Player
    python3 tools/dump_offsets.py --package com.sirket.metin3 --class PlayerController
    python3 tools/dump_offsets.py --package com.sirket.metin3 --auto   # bot icin sablon uret
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import time

SCRIPT = os.path.join(os.path.dirname(__file__), "..", "frida", "il2cpp_dump.js")

# Bir farm botunun ihtiyac duydugu tipik sinif/alan adlari.
# Oyun icindeki gercek adlar farkli olabilir; --find ile dogrulanir.
WANTED_CLASSES = [
    "Player", "PlayerController", "LocalPlayer", "Character", "CharacterController",
    "Hero", "PlayerManager", "GameManager", "EntityManager", "MonsterManager",
    "Monster", "Mob", "Npc", "Entity", "Unit", "Actor",
    "Inventory", "Item", "ItemManager", "SkillManager", "Skill",
    "TargetManager", "CombatManager", "QuestManager",
]

FIELD_HINTS = {
    "hp":       ["hp", "health", "curhp", "currenthp", "nowhp", "life"],
    "max_hp":   ["maxhp", "hpmax", "maxhealth", "totalhp"],
    "mp":       ["mp", "mana", "curmp", "currentmp", "sp"],
    "max_mp":   ["maxmp", "mpmax", "maxmana"],
    "level":    ["level", "lv", "lvl"],
    "exp":      ["exp", "experience", "xp"],
    "position": ["position", "pos", "worldposition", "transform", "coord"],
    "name":     ["name", "charname", "nickname", "playername"],
    "is_dead":  ["isdead", "dead", "isdie", "died", "isalive"],
    "target":   ["target", "curtarget", "currenttarget", "lockedtarget"],
    "gold":     ["gold", "money", "yang", "coin"],
}


def connect(package: str, spawn: bool, device_id: str | None):
    try:
        import frida
    except ImportError:
        sys.exit("[!] frida yok. Kur: pip install frida-tools")

    dev = frida.get_device(device_id) if device_id else frida.get_usb_device(timeout=10)
    print(f"[*] cihaz: {dev.name}")

    if spawn:
        pid = dev.spawn([package])
        session = dev.attach(pid)
        resume = lambda: dev.resume(pid)
    else:
        session = dev.attach(package)
        resume = lambda: None

    with open(SCRIPT, encoding="utf-8") as fh:
        src = fh.read()
    script = session.create_script(src)
    script.on("message", lambda m, d: _on_message(m))
    script.load()
    resume()
    return session, script


def _on_message(msg):
    if msg["type"] == "send":
        print("[oyun]", msg["payload"])
    elif msg["type"] == "error":
        print("[hata]", msg.get("stack", msg.get("description")), file=sys.stderr)


def guess_mapping(cls: dict) -> dict:
    """Alan adlarindan bot icin anlamli isimlendirme tahmini uretir."""
    mapping = {}
    for logical, hints in FIELD_HINTS.items():
        for f in cls.get("fields", []):
            if f.get("static"):
                continue
            low = f["name"].lower().lstrip("_").replace("m_", "")
            if low in hints:
                mapping[logical] = {"field": f["name"], "offset": f["offset"],
                                    "type": f["type"]}
                break
        if logical in mapping:
            continue
        # gevsek eslesme
        for f in cls.get("fields", []):
            if f.get("static"):
                continue
            low = f["name"].lower()
            if any(h in low for h in hints):
                mapping[logical] = {"field": f["name"], "offset": f["offset"],
                                    "type": f["type"], "loose": True}
                break
    return mapping


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--package", required=True, help="uygulama paket adi")
    ap.add_argument("--device", default=None, help="frida cihaz id (varsayilan: usb)")
    ap.add_argument("--attach", action="store_true",
                    help="calisan surece bagla (varsayilan: spawn)")
    ap.add_argument("--wait", type=float, default=8.0,
                    help="il2cpp yuklenmesi icin bekleme (sn)")
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--all", action="store_true", help="tum siniflari dok")
    g.add_argument("--find", metavar="AD", help="isimde gecen siniflari ara")
    g.add_argument("--class", dest="klass", metavar="AD", help="tek sinifi detayli dok")
    g.add_argument("--auto", action="store_true",
                   help="bot icin offsets.json taslagi uret")
    args = ap.parse_args()

    session, script = connect(args.package, spawn=not args.attach, device_id=args.device)
    print(f"[*] il2cpp yuklenmesi icin {args.wait}sn bekleniyor...")
    time.sleep(args.wait)

    os.makedirs("out", exist_ok=True)
    api = script.exports_sync if hasattr(script, "exports_sync") else script.exports

    print("[*] ping:", api.ping())

    if args.find:
        api.find(args.find)

    elif args.klass:
        data = api.klass(args.klass)
        if data:
            path = f"out/class_{args.klass}.json"
            with open(path, "w", encoding="utf-8") as fh:
                json.dump(data, fh, indent=2, ensure_ascii=False)
            print(f"-> {path}")

    elif args.all:
        data = api.dumpall({"skipSystem": True})
        with open("out/il2cpp_dump.json", "w", encoding="utf-8") as fh:
            json.dump(data, fh, indent=2, ensure_ascii=False)
        print(f"-> out/il2cpp_dump.json  ({len(data)} sinif)")

    elif args.auto:
        data = api.dumpall({"skipSystem": True})
        with open("out/il2cpp_dump.json", "w", encoding="utf-8") as fh:
            json.dump(data, fh, indent=2, ensure_ascii=False)
        print(f"-> out/il2cpp_dump.json  ({len(data)} sinif)")

        by_name = {c["name"]: c for c in data}
        draft = {"_kaynak": "tools/dump_offsets.py --auto",
                 "_not": "offsetler dogrulanmadi; bot/verify.py ile teyit et",
                 "module": "libil2cpp.so", "classes": {}}
        for want in WANTED_CLASSES:
            for name, cls in by_name.items():
                if want.lower() != name.lower():
                    continue
                mapped = guess_mapping(cls)
                if not mapped:
                    continue
                draft["classes"][name] = {
                    "full": cls["full"],
                    "instance_size": cls["instanceSize"],
                    "fields": mapped,
                    "statics": [
                        {"field": f["name"], "offset": f["offset"], "type": f["type"]}
                        for f in cls["fields"] if f.get("static")
                    ][:20],
                }
        with open("out/offsets.draft.json", "w", encoding="utf-8") as fh:
            json.dump(draft, fh, indent=2, ensure_ascii=False)
        print(f"-> out/offsets.draft.json  ({len(draft['classes'])} aday sinif)")
        for n, c in draft["classes"].items():
            print(f"   {n}: " + ", ".join(f"{k}@0x{v['offset']:x}"
                                          for k, v in c["fields"].items()))

    session.detach()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
