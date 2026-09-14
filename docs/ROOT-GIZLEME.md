# Oyun açılmıyorsa: sebebi bulup gidermek

Bir emülatörde oyunun açılmamasının üç ayrı sebebi olabilir ve **çözümleri
birbirinden tamamen farklı.** Önce hangisi olduğunu tespit edin:

```bash
python3 tools/why_blocked.py --package com.hardmobile.client
```

Bu araç misafir sistemi bir anti-cheat'in baktığı yerlerden tarar — `su`
dosyaları, root yönetici uygulamaları, emülatör parmak izleri, pairip logları,
ve oyunun son çökme kayıtları — sonra her bulguya karşılık gelen çözümü yazar.

---

## Sebep 1: Root tespiti (en yaygın)

Oyun `/system/bin/su` gibi dosyaları veya Magisk/SuperSU uygulamasını görüyor.

### Çözüm A — root anahtarını kapat (önce bunu deneyin)

Emülatörün "Root izni" anahtarı misafir sisteme `su` binary'sini kurar.
Kapatın, yeniden başlatın. `adbd` zaten root çalıştığı için araçlar etkilenmez:

```bash
adb shell id      # uid=0(root) görmeliyiz
```

Bu tek başına vakaların çoğunu çözer, çünkü oyun ortada `su` göremez.

### Çözüm B — Magisk + DenyList

Anahtarı kapatmak yetmediyse (bazı emülatörler root izlerini sistem imajında
bırakır) root'u gerçekten gizlemek gerekir:

1. Emülatöre **Magisk** kurun (emülatöre göre değişir; LDPlayer için hazır
   Magisk kurulum paketleri dolaşır, MEmu'da da benzer).
2. Magisk → Ayarlar → **Zygisk**'i açın, yeniden başlatın.
3. Magisk → Ayarlar → **DenyList'i zorla** → listeye `com.hardmobile.client`
   ekleyin (tüm alt süreçleri seçin).
4. Magisk uygulamasının kendi adını gizleyin (Ayarlar → Uygulamayı gizle).

Zygisk, DenyList'teki uygulamanın sürecinden root izlerini kaldırır. Bu
anti-root kontrollerine karşı kalıcı çözümdür.

Önemli: Magisk kurulu haldeyken **araçlar `su` kullanmasın**, yoksa oyunun
göreceği süreçte iz bırakabilir. Zaten otomatik algılıyorlar ama garantilemek
için `--no-su` verin.

---

## Sebep 2: Emülatör tespiti

Oyun `ro.kernel.qemu`, `ro.hardware=goldfish/vbox/nox`, `ro.build.tags=test-keys`
gibi değerlerden sanal bir cihazda olduğunu anlıyor.

`why_blocked.py` bu değerleri listeler. Çözüm, onları gerçek bir telefona
benzetmek:

* Bazı emülatörlerde **cihaz profili** ayarı vardır (LDPlayer: Ayarlar → Cihaz
  → marka/model seçimi). Önce bunu deneyin, Samsung/Xiaomi bir model seçin.
* Kalıcısı Magisk + **MagiskHide Props Config** modülüdür; `ro.*` değerlerini
  gerçek bir cihazın parmak izine ayarlar.

---

## Sebep 3: pairip lisans / bütünlük kontrolü

`com.pairip.licensecheck` Google Play'in kurcalama korumasıdır. APK'nın Play
üzerinden kurulduğunu ve imzasının bozulmadığını doğrular.

* Oyunu **mutlaka emülatörün içindeki Play Store'dan kurun.** Elinizdeki
  `HardMobile.apk` yalnızca `base.apk` — split'ler eksik, imza zinciri
  eksik, pairip bunu reddeder.
* Play Store'dan kurulduysa ve hâlâ çöküyorsa, sebep büyük olasılıkla pairip
  değil yukarıdaki iki maddeden biridir. `why_blocked.py`'nin logcat çıktısı
  ayırt eder.

---

## Sebep 4: ARM çevirisi (çökme, "izin vermiyor" değil)

Oyun açılıp hemen kapanıyorsa ve logcat'te `SIGSEGV` + bir `.so` adı
görüyorsanız, sorun root değil: x86 emülatörde arm64 kütüphaneler çeviriliyor
ve çeviri katmanı yetmiyor.

* Emülatör ayarlarından **ARM çevirisini** açın.
* Android 9 (64-bit) imajı kullanın.
* Olmuyorsa arm64 destekli başka bir emülatör deneyin.

---

## Hiçbiri olmazsa

Emülatör yolu kapanırsa elimizde hâlâ iki seçenek var:

* **Ekran tabanlı bot** (`docs/EKRAN-BOTU.md`) — telefonda, root'suz, bellek
  okumadan. Kırılgan ama her koşulda çalışır.
* **GameGuardian sanal alanı** (`docs/GAMEGUARDIAN.md`) — aynı pairip riski
  var ama denemeye değer.
