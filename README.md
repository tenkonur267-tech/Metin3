# m3tools

Termux'tan çalışan bellek tarayıcı, pointer zinciri bulucu ve kural tabanlı farm
botu. Harici bağımlılık yok — sadece Python 3.8+ standart kütüphanesi.

Kaynak kodu elinizde olmayan bir Android oyununda karakter değerlerinin
(HP, MP, altın, koordinat, hedef canı…) bellekteki yerini bulup, bunları oyun
her yeniden başladığında da geçerli olan **modül + offset zincirlerine**
çevirmek için yazıldı. Bulunan zincirler bir "offset tablosunda" saklanır,
farm botu da o tabloyu okuyarak karar verir.

## Neden pointer zinciri?

Taramayla bulduğunuz `0x7b1c2d40f0` gibi bir adres oyunu kapatınca çöpe gider —
heap her açılışta başka yere düşer. Kalıcı olan şey şudur:

```
hp = [ [ [libil2cpp.so + 0x1A2B30] + 0x18 ] + 0x40 ] + 0xC
```

`libil2cpp.so`'nun yüklenme adresi `/proc/<pid>/maps`'ten okunur, offsetler
sabittir. `m3 pointer` tam olarak bu zinciri bulur ve doğrular.

## Gereksinimler

| Ne | Neden | Alternatif |
|---|---|---|
| **root (`su`)** | Başka bir uid'in `/proc/<pid>/mem` dosyasını okumak için | Yok — Android'de app'ler ayrı uid'de çalışır |
| Termux + `pkg install python` | Araçları çalıştırmak | — |
| Yazılabilir `/dev/input/eventN` | Hızlı dokunma enjeksiyonu | `su -c input tap` (yavaş), `adb shell` (kablosuz hata ayıklama) |

Root yoksa bellek okuma mümkün değildir; o durumda ekran görüntüsü + piksel
tabanlı otomasyona geçmek gerekir (bu repoda yok).

Her şeyden önce:

```bash
python3 m3tools/cli.py doctor
```

Root durumunu, `su`/`adb` varlığını ve dokunmatik cihaz düğümünü raporlar.

## Hızlı başlangıç

```bash
git clone https://github.com/tenkonur267-tech/Metin3
cd Metin3
alias m3='python3 '"$PWD"'/m3tools/cli.py'

su                      # bundan sonrası root kabuğunda
m3 doctor
m3 ps hardmobile        # oyunun pid'i
```

### 1. Bir değeri bul (örn. HP)

Oyun ekranında HP'nizin kaç olduğunu görün, sonra:

```bash
m3 scan -p hardmobile -t i32 -v 1500
```

Oyunda hasar alın, yeni değerle daraltın:

```bash
m3 next -v 1320
m3 next -v 1180
m3 list                 # 1-3 aday kalana kadar tekrarlayın
```

Değeri okuyamıyorsanız (bar var ama sayı yok) bilinmeyen-değer araması yapın:

```bash
m3 scan -p hardmobile -t i32 --unknown
m3 next --op decreased   # hasar alın
m3 next --op decreased   # tekrar
m3 next --op unchanged   # bekleyin
```

Adayı doğrulayın — `m3 watch -p hardmobile -a 0x... -t i32` çalışırken oyunda
canınız değişirken sayı da değişmeli.

### 2. Kalıcı pointer zincirine çevir

```bash
m3 pmap -p hardmobile                 # pointer haritası (bir kez, ~30-90sn)
m3 pointer 0x7b1c2d40f0 --name hp --type i32 --note "karakter cani"
```

Çıktı doğrulanmış zincirleri listeler ve `--name` verirseniz
`~/.m3/offsets.json` tablosuna yazar. Tabloyu canlı kontrol edin:

```bash
m3 table -p hardmobile
```

Oyunu **kapatıp açın**, `m3 table -p hardmobile` tekrar çalıştırın: değerler
hâlâ doğru geliyorsa zincir sağlamdır. Gelmiyorsa `--pick 1`, `--pick 2` ile
başka bir zincir deneyin veya `--depth 6 --max-offset 0x2000` ile arayın.

Aynısını `hp_max`, `mp`, `mp_max`, `gold`, `x`, `y` (genelde `f32`),
`target_hp` için tekrarlayın.

### 3. Farm botu

```bash
cp examples/farm.example.json ~/.m3/farm.json
nano ~/.m3/farm.json          # points{} içindeki koordinatları kendi ekranınıza göre düzeltin
m3 farm --dry-run -v          # hiçbir şeye dokunmadan ne yapacağını yazar
m3 farm -v                    # gerçek çalıştırma
```

Ekran koordinatlarını bulmak için: Geliştirici Seçenekleri → "Dokunma verilerini
göster" açın, butona basın, sol üstteki X/Y değerlerini `points`'e yazın.

## Komutlar

| Komut | Ne yapar |
|---|---|
| `m3 doctor` | Root/izin/cihaz kontrolü |
| `m3 ps [desen]` | Süreçleri ve okunabilirliklerini listeler |
| `m3 maps -p PID [--modules]` | Bellek bölgeleri / yüklü modül adresleri |
| `m3 scan -p PID -t TİP -v DEĞER` | İlk tarama (`--unknown` ile değer bilmeden) |
| `m3 next -v DEĞER \| --op changed\|increased\|decreased\|unchanged` | Adayları daraltır |
| `m3 list` | Kalan adaylar, hangi modüle düştükleri |
| `m3 watch -p PID -a ADRES -t TİP` | Adresi canlı izler |
| `m3 write -p PID -a ADRES -t TİP -v DEĞER` | Belleğe yazar |
| `m3 pmap -p PID` | Pointer haritasını kurar ve saklar |
| `m3 pointer ADRES --name AD` | Kalıcı zinciri bulur, doğrular, tabloya yazar |
| `m3 table [-p PID]` | Offset tablosu (pid verilirse canlı değerlerle) |
| `m3 farm [-c config] [--dry-run]` | Kural motorunu çalıştırır |

Desteklenen tipler: `i8 u8 i16 u16 i32 u32 i64 u64 f32 f64`.

## Farm konfigürasyonu

`rules` listesi önceliğe göre sıralanır, tick başına **tek** kural tetiklenir —
böylece çelişen dokunuşlar üst üste binmez. `when` ifadeleri `eval` değildir;
sadece aritmetik, karşılaştırma, `and/or/not` ve `abs/min/max/round/int/float`
içeren beyaz listeli bir AST yorumlayıcısından geçer (bkz. `m3tools/farm/expr.py`).

Eylemler: `tap`, `swipe`, `key`, `wait`, `write`, `log`.
`write` belleğe yazar — dokunma enjeksiyonundan çok daha risklidir ve yanlış
değer istemciyi çökertebilir, o yüzden isteğe bağlıdır.

## Testler

```bash
python3 -m unittest discover -s tests -v
```

`tests/target.c` kasıtlı olarak oyun benzeri üç seviyeli bir pointer zinciri
kuran bir hedef derler; uçtan uca testler taramanın gerçek adresi bulduğunu,
zincirin keşfedildiğini ve **süreç yeniden başlatıldıktan sonra da** doğru
çözüldüğünü doğrular.

## Yasal not

Bu araçlar kendi cihazınızdaki kendi oyununuzu incelemeniz içindir. Başkasına
ait sunucularda kullanımı ilgili hizmetin kullanım şartlarını ihlal edebilir.
