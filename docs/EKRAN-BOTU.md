# Ekran tabanlı farm botu (root gerekmez)

Bellek okuma root istediği için kapalıysa, botu gözle çalıştırırız: ekranı
yakala, can barının ne kadar dolu olduğunu ölç, karar ver, dokun.

Kesin sayıyı göremezsiniz — "canım 1187" bilgisi yok. Ama "can barı %42 dolu"
bilgisi bir farm botu için yeterlidir.

## 1. Girdi ve yakalama yolunu aç

`m3 doctor` hangi backend'in çalıştığını söyler. Root yoksa tek seçenek ADB:

Telefonda **Ayarlar → Geliştirici Seçenekleri → Kablosuz hata ayıklama**'yı
açın, **Cihazı eşleştir** deyin; ekranda bir kod ve `IP:port` çıkacak.
Termux'ta:

```bash
pkg install android-tools -y
adb pair 127.0.0.1:<eşleştirme_portu>
adb connect 127.0.0.1:<bağlantı_portu>
adb devices
```

> Eşleştirme portu ile bağlantı portu **farklıdır**. Eşleştirme penceresindeki
> port `adb pair` içindir; `adb connect` için kablosuz hata ayıklama ana
> ekranında yazan portu kullanın.

`adb devices` cihazı `device` olarak listeliyorsa hazırsınız.

## 2. Ekran görüntüsü al ve koordinatları oku

```bash
m3 shot -o ~/storage/shared/Download/ekran.png
```

PNG'yi galeriden açın. Can barının **başladığı** ve **bittiği** pikselleri, bir
de butonların merkezlerini not edin. Bir noktanın rengini doğrudan sorabilirsiniz:

```bash
m3 shot --at 150,60 --at 520,60 --at 980,1650
```

## 3. Config'i doldur

```bash
mkdir -p ~/.m3
cp examples/farm.screen.example.json ~/.m3/farm.json
nano ~/.m3/farm.json
```

`probes` bölümünde her ölçüm şöyle tanımlanır:

| Tip | Ne ölçer | Alanlar |
|---|---|---|
| `bar` | Barın yüzde kaçı dolu | `from`, `to`, `color`, `tolerance`, `gap` |
| `color` | Bir nokta o renkte mi (1/0) | `at`, `color`, `tolerance` |
| `bright` | Bir kutunun ortalama parlaklığı | `from`, `to`, `step` |

`bar` probu çizgi boyunca yürür ve **ilk boşlukta durur** — böylece ekranın
ilerisindeki başka bir kırmızı nesne ölçümü şişirmez. `gap` kaç ardışık
eşleşmeyen pikselin "bar bitti" sayılacağını belirler; barın üzerindeki parlaklık
efekti ölçümü kesiyorsa `gap`'i büyütün.

## 4. Kalibre et

```bash
m3 probe -w
```

Değerler canlı akar. Oyunda hasar alın: `hp_pct` düşmeli. Düşmüyorsa
`from`/`to` koordinatları veya `color` yanlıştır. Renk için barın **dolu**
kısmından bir piksel seçin, kenarından değil.

## 5. Çalıştır

```bash
m3 farm --dry-run -v
```

Hiçbir şeye dokunmadan hangi kuralın tetiklendiğini yazar. Kurallar doğru
tetikleniyorsa:

```bash
m3 farm -v
```

## Sınırlar

* **Kırılgandır.** Çözünürlük, arayüz ölçeği, tema veya oyun güncellemesi
  koordinatları bozar. Her değişiklikte yeniden kalibre etmek gerekir.
* **Gecikmelidir.** Her tick bir ekran yakalama demek; ADB üzerinden bu
  ~100-300 ms sürer. `poll_interval`'ı 0.3'ün altına indirmenin faydası yok.
* **`write` eylemi çalışmaz.** Ekran kaynağı belleğe yazamaz; config'de
  `write` kullanırsanız bot açık bir hatayla durur.
* Bellek yolu açılırsa (root'lu bir emülatör, VM ya da cihaz) config'de
  `"source": "memory"` yapmak yeterlidir — kurallar aynen çalışır.
