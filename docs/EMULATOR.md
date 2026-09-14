# PC + emülatör: VS Code'dan tarama

Root'suz bir telefonda bellek okumak mümkün değil. Bilgisayarda ise oyunu
**root'lu bir Android emülatöründe** çalıştırıp belleğine serbestçe
erişebilirsiniz. Araçlar bu durumda `-D adb` ile emülatörü hedef alır ve
VS Code terminalinden çalışır.

## 1. Emülatör

**Windows: LDPlayer 9.** Play Store'u ve tek tuşla root'u birlikte sunan
pratik seçenek — ikisi de gerekli.

Kurulum ayarları:

| Ayar | Değer | Neden |
|---|---|---|
| Android sürümü | 9 (64-bit) | Oyunun native kütüphaneleri arm64 |
| ARM çevirisi | Açık | bgfx tabanlı native motor x86'da çeviri ister |
| Root izni | Açık | Belleği okumanın tek yolu |
| CPU / RAM | 4 çekirdek / 4 GB | Tarama bellek ister |

Ayarları değiştirdikten sonra emülatörü yeniden başlatın.

**Oyunu emülatörün içindeki Play Store'dan kurun**, elinizdeki
`HardMobile.apk`'dan değil. O dosya yalnızca `base.apk`; split'ler eksik
olduğu için native kütüphaneler yok ve kurulsa bile çöker. Play'den kurmak
ayrıca `com.pairip.licensecheck` lisans doğrulamasını da düzgün geçirir.

Diğer sistemler: Linux'ta **Waydroid** (oyunun süreci host'ta doğrudan
görünür, `-D adb` bile gerekmez), macOS'ta **Genymotion**.

## 2. Bağlantı

Platform-tools'u kurup `adb`'yi PATH'e ekleyin, sonra VS Code terminalinde:

```bash
adb connect 127.0.0.1:5555
adb devices
```

LDPlayer 5555 kullanır; MEmu 21503, Nox 62001. Emülatörün ayarlarında yazar.

Root'u doğrulayın:

```bash
adb shell su -c id
```

`uid=0(root)` görmelisiniz. Görmüyorsanız emülatör ayarlarından root iznini
açıp yeniden başlatın.

## 3. Araçlar

Her komuta `-D adb` ekleyin; gerisi telefondaki akışın aynısı:

```bash
python3 m3tools/cli.py -h            # alt komut listesi
python3 m3tools/cli.py ps -D adb hardmobile
python3 m3tools/cli.py scan -D adb -p com.hardmobile.client -t i32 -v 1500
python3 m3tools/cli.py next -v 1320
python3 m3tools/cli.py list
python3 m3tools/cli.py pointer 0x7b1c2d40f0 --name hp --type i32
python3 m3tools/cli.py table -D adb -p com.hardmobile.client
```

`next`, `list` ve `watch` komutlarına `-D` vermenize gerek yok: tarama hangi
cihazda başlatıldıysa onu hatırlar.

Birden fazla emülatör açıksa `--serial` ile seçin:

```bash
adb devices
python3 m3tools/cli.py scan -D adb --serial 127.0.0.1:5555 ...
```

## Hız

Uzaktan her okuma bir süreç açılışı ve bir gidiş-dönüş demek; yerel okuma ise
tek bir sistem çağrısı. Araçlar bunu iki şekilde telafi eder: bölgeleri 8 MB'lık
bloklar halinde okur, ve aday adresleri ayrı ayrı değil 4 MB'lık gruplar
halinde tazeler.

Pratikte loopback üzerinden ilk tarama birkaç dakika, sonraki daraltmalar
saniyeler sürer. Yine de en hızlı yol emülatörün **içine Termux kurup**
araçları orada çalıştırmaktır — o zaman `-D` gerekmez, her şey yerel olur.
Tarama uzun sürüyorsa bu seçeneği değerlendirin.

## Farm botu

`~/.m3/farm.json` içine `"device": "adb"` ekleyin; kurallar aynen çalışır.
Dokunma göndermek için `backend` alanı `adb` olmalı:

```json
{
  "process": "com.hardmobile.client",
  "source": "memory",
  "device": "adb",
  "backend": "adb"
}
```

## Riskler

**Oyun emülatörde açılmayabilir.** `com.pairip.licensecheck` emülatör tespiti
yapabiliyor. Ayrıca arm64 çevirisi native bir motoru zorlar; açılsa bile
kasabilir. İlk kontrol edilecek şey budur.

**Sunucu otoritesi değişmedi.** Emülatör size belleği okuma yetkisi verir, ama
Metin2 sunucu otoriter bir oyundur: can, hasar ve item sunucuda tutulur.
İstemcideki değeri değiştirmek çoğu zaman yalnızca ekrandaki sayıyı değiştirir.
Bulunan değerleri okumak (bot kararları için) her zaman işe yarar; yazmak
yalnızca sunucunun doğrulamadığı alanlarda işe yarar.

## Oyun "root tespit edildi" diyorsa

Oyunun gördüğü root ile araçların ihtiyaç duyduğu root aynı şey değil, ve bu
ayrım sorunu çözer.

Emülatörlerin "Root izni" anahtarı misafir sisteme bir `su` binary'si ve bir
Superuser uygulaması kurar. Anti-root kontrolleri tam olarak bunlara bakar:
`/system/bin/su` var mı, Superuser paketi kurulu mu, `su` çalıştırılabiliyor mu.

Oysa emülatörlerde `adbd` zaten root olarak çalışır — anahtar **kapalıyken de**
`adb shell` size uid 0 verir. Yani:

1. Emülatör ayarlarından **root iznini kapatın**, yeniden başlatın.
2. Doğrulayın:

```bash
adb shell id
```

`uid=0(root)` görüyorsanız iş bitti: oyun ortada `su` göremez, araçlar yine de
belleği okur. Araçlar bunu kendiliğinden algılar — `su` gerekmiyorsa
kullanmazlar. Zorlamak isterseniz `--no-su` verin:

```bash
python3 m3tools/cli.py ps -D adb --no-su hardmobile
```

`adb shell id` size `uid=2000(shell)` diyorsa o emülatörde adbd root değildir.
Sırayla deneyin:

* `adb root` (bazı emülatörler bunu destekler)
* Emülatörün "geliştirici" / "hata ayıklama" modunu açın
* Başka bir emülatör (MEmu'nun adbd'si genelde root çalışır)

### Bu da yetmezse: Magisk + DenyList

Root'u tamamen gizlemek gerekiyorsa emülatöre Magisk kurup oyunu **DenyList**'e
eklemek gerekir; Zygisk oyunun sürecinde root izlerini gizler. Bu daha uğraşlı
ve emülatöre göre değişir, ama anti-root kontrollerine karşı kalıcı çözümdür.

### Neden Waydroid bu işte daha iyi

Linux'taysanız Waydroid bu sorunu hiç yaşatmaz: misafir sistemde `su` yoktur,
Superuser uygulaması yoktur, oyun tertemiz bir Android görür — ama siz host'ta
zaten root olduğunuz için oyunun süreci sıradan bir Linux süreci olarak
okunabilir. Anti-root açısından en temiz kurulum budur.
