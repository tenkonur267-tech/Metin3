# Android IL2CPP analiz araclari

Android/Unity IL2CPP uygulamalarini incelemek icin genel amacli tersine
muhendislik araclari. Hicbir oyuna ozel veri icermez - hedef uygulamayi
kullanici kendi saglar.

## Durum

Tooling iskeleti kuruldu, **hedef uzerinde calistirilmadi**. Depoda analiz
edilmis bir uygulama yok.

## Icerik

| Dosya | Isi |
|---|---|
| `tools/engine_detect.py` | APK'dan oyun motorunu tespit eder (IL2CPP / Mono / Unreal / Godot / Cocos2d), ABI'leri ve native kutuphaneleri listeler, `global-metadata.dat` surumunu okur, packer izlerini arar. Frida gerektirmez, tamamen statik. |
| `frida/il2cpp_dump.js` | Calisan bir IL2CPP surecinde `il2cpp_*` runtime API'si uzerinden sinif / alan / metod offsetlerini cikarir. Tahmin yok - motor degerleri birebir verir. |
| `tools/dump_offsets.py` | Yukaridaki script'i Frida ile surecte calistirip sonucu JSON'a yazan surucu. |

## Kullanim

Statik analiz (cihaz gerekmez):

```bash
python3 tools/engine_detect.py yol/uygulama.apk
```

Dinamik analiz icin cihazda root + `frida-server`, PC'de `pip install frida-tools`
gerekir:

```bash
python3 tools/dump_offsets.py --package com.ornek.uygulama --find Player
python3 tools/dump_offsets.py --package com.ornek.uygulama --class PlayerController
```

## Kapsam

Bu araclar yalnizca **inceleme yetkisine sahip oldugun** uygulamalar icin
kullanilmalidir: kendi gelistirdigin uygulamalar, kendi isletttigin sunucunun
istemcisi, ya da acikca izin veren CTF / crackme hedefleri. Baskasinin
isletttigi cok oyunculu bir sunucuda avantaj saglamak icin kullanilmasi
amaclanmamistir.
