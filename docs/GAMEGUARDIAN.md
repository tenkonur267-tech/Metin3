# GameGuardian ile değer tabanlı bot

Root yokken bellek okumanın tek yolu, oyunu belleğini okuyabildiğiniz bir
sürecin içinde çalıştırmak. GameGuardian'ın root'suz modu bunu "sanal alan"
ile yapar: oyunu kendi uid'i altında başlatır, dolayısıyla onu ptrace
edebilir.

`gg/m3gg.lua` bu ortamda çalışan bir betiktir. Değer arar, bulduğu adresi
**bölge adı + offset** olarak kalıcı hale getirir, ve koşullu bir yazma
döngüsü çalıştırır.

## Önce beklentiyi ayarlayalım

Metin2 istemci-sunucu bir oyundur. Can, hasar, item, deneyim gibi değerler
**sunucuda** tutulur; istemcideki kopyayı değiştirmek çoğu zaman yalnızca
ekrandaki sayıyı değiştirir ve sunucu bir sonraki pakette üzerine yazar.

Değer düzenlemenin gerçekten iş gördüğü yerler, sunucunun doğrulamadığı
alanlardır — genelde hareket hızı, saldırı aralığı, kamera mesafesi,
istemci tarafı sayaçlar. Bu her sunucuda farklıdır.

Bu yüzden sıra şu olmalı: **önce ölç, sonra otomatikleştir.** Bir değeri
bulun, değiştirin, ve oyunda gerçekten değişip değişmediğine bakın. Sunucu
geri alıyorsa o değer bot için kullanışsızdır.

## Kurulum

1. GameGuardian'ı kurun ve root'suz (sanal alan) modunu seçin.
2. HardMobile'ı GG'nin sanal alanına kurun.
3. Oyunu sanal alandan başlatın.

> `com.hardmobile.client` içinde `com.pairip.licensecheck` var — Google
> Play'in kurcalama koruması. Sanal alanda oyun Play üzerinden kurulmuş
> sayılmaz ve lisans doğrulaması düşebilir, yani istemci hiç açılmayabilir.
> İlk kontrol edilecek şey budur: oyun sanal alanda açılıyor mu?

Betiği telefona koyun:

```bash
mkdir -p ~/storage/shared/Download/m3gg
cp ~/Metin3/gg/m3gg.lua ~/storage/shared/Download/m3gg/
```

GG'yi açın → menü → **Betikler** → `m3gg.lua` seçin.

## Kullanım

### 1. Değer ara

**Değer ara** → tipi seçin (can genelde `DWORD`, koordinatlar `FLOAT`).

Ekranda sayı görüyorsanız "Değeri biliyorum" deyin ve girin. Sonra oyunda
hasar alıp **Yeni değerle daralt** ile tekrarlayın.

Sadece bar görüyorsanız "Değeri bilmiyorum" deyin; menü **Azaldı / Arttı /
Değişmedi / Değişti** seçenekleri sunar. Hasar alın → Azaldı. İksir için →
Arttı. Bekleyin → Değişmedi. Üç-beş adımda aday sayısı avuç içine iner.

### 2. Kaydet

Aday sayısı azalınca **Bu sonucu kaydet** → adresi seçin → bir isim verin
(`hp`, `mp`, `gold`).

Betik adresin hangi bellek bölgesinde olduğuna bakar. `libX.so` gibi
**adlandırılmış** bir bölgedeyse, o bölgenin başlangıcına göre offset'i
hesaplayıp saklar — bu giriş oyun yeniden açıldığında da çalışır.

Adres **anonim** bir bölgedeyse (heap), bölgenin başlangıcı her açılışta
değiştiği için offset anlamsız olur; betik sizi uyarır ve adresi yalnızca bu
oturum için mutlak olarak saklar. Kalıcı bir giriş istiyorsanız değeri bir
kütüphane bölgesinde bulmaya çalışın.

### 3. Yapı haritası

Bir değeri bulduktan sonra **Yapı haritası**'nı kullanın. Seçtiğiniz adresin
çevresindeki baytları hem tamsayı hem ondalık olarak döker.

Bu, komşu alanları tek tek aramaktan çok daha hızlıdır: canı bulduysanız
maksimum can, mana ve seviye neredeyse kesinlikle aynı nesnenin içinde,
birkaç bayt ötededir. `1500` yanında `1500` görüyorsanız o maksimum candır;
`-42.25` gibi bir ondalık görüyorsanız muhtemelen koordinattır.

### 4. Farm döngüsü

**Giriş düzenle** menüsünden bir girişi **farm döngüsüne ekleyin**. İsterseniz
bir koşul verin: "yalnızca şu değerin altındayken yaz". Örneğin `hp` girişine
değer `5000`, koşul `1000` verirseniz, can 1000'in altına düştüğünde 5000
yazılır — sürekli yazmak yerine.

Sonra **Farm döngüsünü başlat**. Menüye dönmek için GG ikonuna dokunun.

Koşulsuz girişler her turda yazılır; bu GG'nin normal dondurma listesiyle
aynı şeydir. Koşul desteği bu betiğin eklediği farktır.

## Sorun giderme

| Belirti | Sebep |
|---|---|
| Oyun sanal alanda açılmıyor | pairip lisans kontrolü. GG'nin bu oyunda kullanılamayacağı anlamına gelir. |
| Arama hiç sonuç bulmuyor | Yanlış tip. Can için `DWORD` olmazsa `FLOAT` deneyin. |
| Aday sayısı azalmıyor | Değer o an gerçekten değişmiyor; oyunda değiştirdiğinizden emin olun. |
| Yazılan değer hemen geri dönüyor | Sunucu o değeri doğruluyor. O değer bot için kullanılamaz. |
| Giriş `ÇÖZÜLEMEDİ` diyor | Kütüphane henüz yüklenmedi, ya da giriş anonim bölgedeydi ve oyun yeniden başladı. |

## Testler

Betiğin saf mantığı (offset aritmetiği, kalıcı kayıt biçimi, taşınmış
kütüphanede yeniden çözümleme) sahte bir `gg` ile test edilir:

```bash
lua5.4 tests/test_gg.lua
```
