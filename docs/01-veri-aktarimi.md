# Analiz verisini telefondan depoya aktarma

Sorun: oyun telefonda, analiz araclari burada. Cozum dosyalari elden
gondermek degil - **depoyu telefona klonlayip analizi orada calistirmak**,
sonra ciktiyi push etmek. PC gerekmez, ilk asama icin root da gerekmez.

---

## Asama 1 - Motor tespiti (PC yok, root yok)

Google Play'den degil, **F-Droid'den Termux** kur (Play surumu eskidir ve
paket yoneticisi bozuktur): https://f-droid.org/packages/com.termux/

Termux'ta:

```bash
pkg update -y && pkg install -y python git unzip
git clone https://github.com/tenkonur267-tech/Metin3
cd Metin3
git checkout claude/game-auto-farm-bot-7ic6c4
```

### Paket adini bul

Yuklu ucuncu parti uygulamalari listele (kisa liste, gozle bulunur):

```bash
pm list packages -3
```

Oyunun satirini sec. Ornek cikti: `package:com.hard.mobile` ->
paket adi `com.hard.mobile`.

### APK'yi kopyala

```bash
pm path com.hard.mobile
```

Bir veya birden fazla satir doner (`base.apk` + split'ler). Hepsini kopyala:

```bash
mkdir -p input/apk
for p in $(pm path com.hard.mobile | sed 's/^package://'); do
  cp "$p" input/apk/ 2>/dev/null && echo "kopyalandi: $p"
done
ls -lh input/apk/
```

> Kopyalama `Permission denied` verirse: APK dosyasi okunabilir ama ust
> klasor korumali olabilir. O durumda "APK Extractor" tarzi bir uygulama
> ile APK'yi Downloads'a cikar, sonra
> `cp /sdcard/Download/*.apk input/apk/` ile al.

### Calistir

```bash
python3 tools/engine_detect.py input/apk/
```

Ekrana motoru, ABI'leri, native kutuphaneleri ve koruma (packer) durumunu
basar; ayrica `out/engine_report.json` yazar.

---

## Asama 2 - Sonucu bana ulastir

Terminal ciktisini sohbete yapistirman en hizlisi. Kalici kayit icin
push et:

```bash
git config user.name "onur"
git config user.email "onurtenk79@gmail.com"
git add -f out/engine_report.json
git commit -m "engine report: cihazdan"
git push -u origin claude/game-auto-farm-bot-7ic6c4
```

`-f` gerekli, cunku `out/` normalde `.gitignore` icinde.

Push icin GitHub sifresi degil **personal access token** istenir:
github.com -> Settings -> Developer settings -> Personal access tokens ->
Fine-grained token -> bu depoya `Contents: Read and write` yetkisi.

> **APK'yi push ETME.** Yuzlerce MB, GitHub tek dosyada 100MB siniri
> koyuyor ve `.gitignore` zaten `*.apk` / `*.so` disliyor. Bana sadece
> analiz ciktisi lazim, binary'nin kendisi degil.

---

## Asama 3 - Offsetler (motor belli olduktan sonra)

Asama 1'in sonucu buradaki yolu belirler:

**IL2CPP cikarsa** - en iyi senaryo. Offsetleri tahmin etmeye gerek yok,
`il2cpp_field_get_offset()` birebir veriyor. Iki yol:

- *Root varsa*: telefonda `frida-server` calistir, `frida/il2cpp_dump.js`
  dogrudan surece baglanir. En temizi.
- *Root yoksa*: `global-metadata.dat` + `libil2cpp.so` uzerinden statik
  dump (Il2CppDumper). Cihazda calisir, oyunun acik olmasi gerekmez.
  Runtime adresleri vermez ama tum sinif/alan offsetlerini verir.

**Mono cikarsa** - `Assembly-CSharp.dll` dogrudan decompile edilir,
offset kavrami bile yok, kaynak kodu okur gibi olur.

**Native/bilinmeyen cikarsa** - `readelf` ile sembol tablosu, sonra
disassembly. En zahmetli yol.

**Packer tespit edilirse** - once unpack gerekir, yoksa dosyalar sifreli
gorunur.

---

## Sik sorulan

**"Oyun acik olmali mi?"** Asama 1 ve 2 icin hayir, dosyalar diskten
okunuyor. Sadece Frida ile canli analizde (Asama 3, root'lu yol) oyunun
acik olmasi gerekir.

**"Telefonum root'lu degil, is biter mi?"** Biter. Statik dump root
istemez. Root sadece calisan surecin bellegine bakmayi ve canli hook'u
mumkun kilar.
