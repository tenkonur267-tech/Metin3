#!/usr/bin/env python3
"""Renderer3D içindeki GLSL kaynaklarını çıkarıp glslangValidator ile derler.

Shader hataları ancak cihazda, oyun açılırken patlar; bu betik onları
derleme aşamasında yakalar. Kullanım:

    sudo apt-get install -y glslang-tools
    python3 android/tools/check_shaders.py
"""
import os
import re
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app/src/main/java/com/karargah/survival/engine/Renderer3D.java")


def grab(src: str, name: str) -> str:
    m = re.search(r'String %s = ""(.*?);\n' % name, src, re.S)
    if not m:
        sys.exit("Shader bulunamadı: " + name)
    parts = re.findall(r'"((?:[^"\\]|\\.)*)"', m.group(1))
    return "".join(p.encode().decode("unicode_escape") for p in parts)


def main() -> int:
    src = open(SRC, encoding="utf-8").read()
    vs, fs = grab(src, "VS"), grab(src, "FS")
    shaders = {
        "static.vert": "#version 300 es\n" + vs,
        "skinned.vert": "#version 300 es\n#define SKINNED 1\n" + vs,
        "main.frag": "#version 300 es\n" + fs,
        "sky.vert": "#version 300 es\n" + grab(src, "SKY_VS"),
        "sky.frag": "#version 300 es\n" + grab(src, "SKY_FS"),
        "particle.vert": "#version 300 es\n" + grab(src, "PART_VS"),
        "particle.frag": "#version 300 es\n" + grab(src, "PART_FS"),
    }
    failed = 0
    with tempfile.TemporaryDirectory() as tmp:
        for name, code in shaders.items():
            path = os.path.join(tmp, name)
            with open(path, "w", encoding="utf-8") as f:
                f.write(code)
            res = subprocess.run(["glslangValidator", path],
                                 capture_output=True, text=True)
            if res.returncode == 0:
                print("  OK    %s (%d satır)" % (name, len(code.splitlines())))
            else:
                failed += 1
                print("  HATA  %s\n%s" % (name, res.stdout + res.stderr))
    if failed:
        print("%d shader derlenemedi" % failed)
        return 1
    print("Bütün shader'lar GLSL ES 3.00 olarak derlendi.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
