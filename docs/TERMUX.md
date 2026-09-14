# Termux kurulumu ve saha rehberi

## 1. Kurulum

```bash
pkg update && pkg upgrade
pkg install python git
git clone https://github.com/tenkonur267-tech/Metin3
cd Metin3
echo "alias m3='python3 $PWD/m3tools/cli.py'" >> ~/.bashrc
source ~/.bashrc
```

## 2. Root

Android'de her uygulama ayrı bir uid altında çalışır. Termux'un uid'i oyunun
uid'inden farklıdır, dolayısıyla `/proc/<oyun_pid>/mem` dosyasını **root
olmadan açamazsınız** — `Permission denied` alırsınız. Bu bir araç eksikliği
değil, çekirdek seviyesinde bir izin sınırıdır.

```bash
su          # Magisk/KernelSU izin isteyecek
m3 doctor   # 'uid: 0 (root)' görmelisiniz
```

`tsu` paketi (`pkg install tsu`) Termux ortam değişkenlerini koruyarak root
kabuğu açar, alias'ınız bozulmaz:

```bash
tsu
```

## 3. Oyunun pid'ini bulma

```bash
m3 ps hardmobile
```

Çıktı `pid  paket.adı  ok` şeklindedir. `ok` yerine `izin yok` yazıyorsa root
kabuğunda değilsinizdir.

Birden fazla süreç çıkabilir (`:push`, `:gl` gibi alt süreçler). Oyun verisi
neredeyse her zaman **ana süreçtedir** — paket adı iki nokta üst üste
içermeyen satır. Emin olmak için:

```bash
m3 maps -p <pid> --modules | grep -iE 'il2cpp|unity|main|game|mono|UE4|cocos'
```

Bu aynı zamanda motoru da söyler:

| Gördüğünüz kütüphane | Motor | Not |
|---|---|---|
| `libil2cpp.so` | Unity (IL2CPP) | Statik kökler genelde `libil2cpp.so`'nun `.bss`'inde |
| `libmono.so` / `libmonobdwgc-2.0.so` | Unity (Mono) | Managed heap ayrı, GC nesneleri taşıyabilir |
| `libUE4.so` / `libUnreal.so` | Unreal | GNames/GObjects tabanlı |
| `libcocos2d*.so` | Cocos2d-x | |
| `libgame.so`, `libmain.so` | Özel/native motor | |

Motor Mono ise GC nesneleri **taşıyabilir**; zincirin kökünü mümkün olduğunca
`.bss`'e yakın tutun ve `m3 table -p <pid>` ile birkaç dakika arayla doğrulayın.

## 4. Hangi değer hangi tip?

| Değer | Genelde | İpucu |
|---|---|---|
| HP / MP / maksimumları | `i32` | Bazı motorlarda `f32` |
| Altın / yang / deneyim | `i32` veya `i64` | Büyükse `i64` deneyin |
| X / Y / Z koordinat | `f32` | `--align 4` |
| Seviye, stat puanları | `i32` | |
| Hedef canı | `i32` veya `f32` | Genelde HP ile aynı tip |

Tip yanlışsa tarama hiçbir şey bulamaz; sonuç 0 çıkarsa önce tipi değiştirin.

## 5. Tarama taktikleri

**Değeri görebiliyorsanız** (ekranda 1500/1500 yazıyor):

```bash
m3 scan -p hardmobile -t i32 -v 1500
# hasar al
m3 next -v 1320
m3 next -v 1180
```

**Sadece bar görüyorsanız**:

```bash
m3 scan -p hardmobile -t i32 --unknown   # ~10-40M aday, normal
m3 next --op decreased                   # hasar al, sonra çalıştır
m3 next --op decreased
m3 next --op unchanged                   # hiçbir şey yapmadan bekle
m3 next --op increased                   # iksir iç
```

3-5 adımda aday sayısı avuç içine iner.

**Yavaşsa**: ilk tarama tüm yazılabilir belleği okur. `--no-libs` veya
`--no-anon` ile daraltabilirsiniz ama değeri kaçırma riski artar. Önce tam
tarama yapın; darboğaz genelde ilk taramadır, sonraki `next` adımları saniyeler
sürer.

## 6. Zinciri bulma ve sağlamlaştırma

```bash
m3 pmap -p hardmobile                      # pointer haritası, bir kez
m3 pointer 0x7xxxxxxxxx --name hp --type i32
```

Zincir bulunamazsa sırayla deneyin:

1. `--max-offset 0x4000` — nesne büyükse offset daha uzakta olabilir
2. `--depth 7` — daha derin zincir
3. `m3 pmap -p hardmobile --stack` — kök bir thread stack'inde olabilir
4. `--rebuild` — oyun kütüphane yükledikten sonra harita eskimiş olabilir

**Sağlamlık testi (atlamayın):**

```bash
m3 table -p hardmobile        # değerler doğru mu?
# oyunu tamamen kapat, yeniden aç, karaktere gir
m3 ps hardmobile              # yeni pid
m3 table -p <yeni_pid>        # hâlâ doğru mu?
```

İki açılışta da doğruysa zincir kalıcıdır. Değilse `m3 pointer ... --pick 1`
ile listedeki bir sonraki zinciri tabloya yazın ve tekrar test edin. Kısa
zincirler (derinlik 2-3) genelde daha sağlamdır.

## 7. Dokunma enjeksiyonu

`m3 doctor` hangi backend'in çalışacağını söyler:

* **evdev** — `/dev/input/eventN`'e doğrudan yazar, ~1ms. En iyisi.
* **su** — `su -c input tap`, ~150-300ms. Her cihazda çalışır ama farm döngüsü
  için yavaş.
* **adb** — kablosuz hata ayıklamayı telefonun kendisine eşlerseniz.

Koordinatları bulmak: Geliştirici Seçenekleri → **Dokunma verilerini göster**.
Butona basın, ekranın üstündeki X/Y'yi okuyun, `farm.json` içindeki `points`
bölümüne yazın.

## 8. Sorun giderme

| Belirti | Sebep / çözüm |
|---|---|
| `izin yok - hedef başka bir uid` | Root kabuğunda değilsiniz. `su` veya `tsu`. |
| `m3 scan` 0 aday buldu | Yanlış tip (`-t f32` deneyin) ya da yanlış pid (alt süreç). |
| Aday sayısı hiç azalmıyor | Değer o an değişmiyor; oyunda gerçekten değiştirdiğinizden emin olun. |
| `pid ... artık yok` | Oyun yeniden başladı. Tarama uçar — bu yüzden zincir çıkarıyoruz. |
| Zincir `ÇÖZÜLEMEDİ` | Oyun güncellendi (offsetler kaydı) ya da kütüphane henüz yüklenmedi. Karaktere girip tekrar deneyin. |
| Bot hiçbir şey yapmıyor | `m3 farm --dry-run -v` ile kuralların tetiklenip tetiklenmediğine bakın. |
| `config hataları: bilinmeyen isim` | Kuralda kullandığınız isim offset tablosunda yok; `m3 pointer --name <isim>` ile ekleyin. |

## 9. Güvenli çalışma alışkanlıkları

* Önce **sadece okuma** ile çalışın. `write` eylemine ancak zincirlerin sağlam
  olduğundan emin olduktan sonra geçin.
* Her `m3 farm` oturumunu `--dry-run` ile başlatın.
* `stop_after` alanını ayarlayın ki bot süresiz çalışıp telefonu yormasın.
* Yanlış adrese yazmak istemciyi anında çökertir; `m3 watch` ile adresi
  doğrulamadan yazmayın.
