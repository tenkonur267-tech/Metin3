# Root olmadan ne yapılabilir, ne yapılamaz

`tsu` → `No superuser binary detected` çıktısı telefonun root'lu olmadığını
söylüyor. Bu, bu repodaki yol haritasını doğrudan değiştiriyor.

## Neden bellek taraması kapalı

Android'de her uygulama kendi uid'i altında çalışır. `/proc/<pid>/mem`
dosyasını açmak, hedef sürece `ptrace` yetkisi gerektirir; çekirdek bunu
yalnızca aynı uid'e veya `CAP_SYS_PTRACE` sahibi bir sürece verir. Termux
(uid 10402) oyunun (başka bir uid) belleğini okuyamaz. Dahası Android 9'dan
beri `/proc` `hidepid=2` ile bağlandığı için başka süreçleri **listeleyemez**
bile — `m3 ps` çıktısının boş gelmesinin sebebi budur.

Sık sorulan iki kısayol da çalışmaz:

* **ADB / kablosuz hata ayıklama** — `adb shell` uid 2000 (`shell`) altında
  çalışır. Bu uid de hata ayıklanabilir olmayan bir uygulamayı ptrace edemez.
  ADB dokunma gönderebilir ve ekran görüntüsü alabilir, ama belleği okuyamaz.
* **Shizuku** — ADB ile aynı yetki seviyesini verir, dolayısıyla aynı sınır.

Yani `m3 scan` / `m3 pointer` bu telefonda çalışmaz. Araçlar root'lu bir
cihazda ya da bir emülatörde (çoğu emülatör root'ludur) aynen çalışır.

## Root olmadan çalışan üç yol

### A. Statik APK analizi — offsetler taramadan çıkar

Oyun Unity/IL2CPP ise offsetleri **aramaya gerek yok**: sınıf ve alan
offsetleri `global-metadata.dat` dosyasının içinde yazılıdır. APK'yı okumak
için root gerekmez.

```bash
pm list packages -3
pm path com.hard.mobile
mkdir -p input/apk
for p in $(pm path com.hard.mobile | sed 's/^package://'); do
  cp "$p" input/apk/ 2>/dev/null && echo "kopyalandi: $p"
done
python3 tools/engine_detect.py input/apk/
```

Çıktı motoru, ABI'leri ve metadata sürümünü verir. `unity-il2cpp` çıkar ve
metadata şifresizse, Il2CppDumper sınıf/alan offsetlerinin tamamını üretir —
`Player.hp` alanının nesne başlangıcından kaç bayt sonra olduğunu tahmin
etmeye gerek kalmaz.

Bu aşama **bilgi** verir; çalışan oyundan değer okumak için hâlâ B veya C
gerekir.

### B. Ekran tabanlı otomasyon — root yok, bellek yok

Kablosuz hata ayıklamayı telefonun kendisine eşleyip (Android 11+):

* `adb shell screencap` ile ham çerçeve alınır (piksel verisi başlıklı ham
  RGBA olarak gelir; PNG çözmeye gerek yok),
* can barının piksel doluluğu, buton varlığı, renk eşiği gibi ölçütlerle karar
  verilir,
* `adb shell input tap` ile dokunma gönderilir.

Bellekteki kesin sayıyı göremezsiniz, ama "can barı %40'ın altında" bilgisi
bir farm botu için fazlasıyla yeterlidir. Kararlılığı düşüktür (UI değişirse
bozulur), ama hiçbir ek yetki istemez.

### C. APK'yı hata ayıklanabilir hale getirip yeniden imzalamak

APK'ya `android:debuggable="true"` eklenip yeniden imzalanırsa `run-as` ile
kendi sürecinin belleğine erişilebilir; ya da bir Frida gadget'ı gömülerek
runtime'a tam erişim sağlanır. Root gerektirmez ve kendi oyununuz için
meşrudur, ama:

* APK'nın imzası değişir — sunucu imza doğrulaması veya Play Integrity
  kullanıyorsa istemci reddedilebilir,
* split APK'lı oyunlarda yeniden paketleme zahmetlidir,
* oyunun güncellemesi her seferinde işlemi tekrarlatır.

## Karar

Sıralama şu olmalı:

1. **A'yı şimdi yapın** — bedava, risksiz, ve motoru öğrenmeden diğer iki
   yoldan hangisinin mantıklı olduğunu bilemeyiz.
2. Motor IL2CPP çıkarsa ve gerçek değerlere erişmek istiyorsanız **C**;
   sadece çalışan bir farm botu istiyorsanız **B** yeterlidir.
3. Cihazı root'lamak (Magisk) her şeyi açar ama garantiyi düşürür, banka /
   Google Pay uygulamalarını bozabilir ve yanlış imaj ile cihaz açılmayabilir.
   Bunu ancak riskini kabul ediyorsanız düşünün.
