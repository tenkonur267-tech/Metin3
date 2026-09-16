# Son Karargâh — 3B açık dünya zombi survival

Android için yazılmış **gerçek bir native oyun**: tarayıcı yok, WebView yok,
oyun motoru bağımlılığı yok. Tüm 3B motor (OpenGL ES 3.0), ses sentezi,
fizik ve arayüz bu depodaki Java kodudur. Çıktı doğrudan kurulabilir bir APK'dır.

## Oyun

**100.000 x 100.000 birimlik açık bir dünyada** hayatta kalırsın. Dalga sistemi
yoktur: evre, geri sayım, "dalgayı başlat" düğmesi ya da doğma kapısı yok.
Bunun yerine gerçek bir survival döngüsü var — **gündüz keşfe çıkıp kaynak
toplar, geceleri kampını savunursun.**

### Dünya

- **Harita hiçbir yerde saklanmaz.** Her nokta yalnızca koordinatından
  hesaplanır (`WorldGen`), dolayısıyla dünya devasa olmasına rağmen bellekte
  yer kaplamaz ve aynı yere döndüğünde aynı manzara seni karşılar.
- **Yedi biyom:** ova, orman, bataklık, çöl, tundra, harabe ve şehir. Her
  biyomun kendi zemini, bitki örtüsü ve zombi yoğunluğu var.
- **Şehirler:** kafes üzerine serpilmiş merkezler, sokak ızgarası ve binalar.
  Binaların içinden geçilmez; aralarında dolaşıp bina önlerindeki sandıkları
  yağmalarsın. Şehirler en zengin ama en tehlikeli yerlerdir.
- **Zombi yoğunluğu yere göre değişir:** çölde ve tundrada neredeyse kimse yok,
  harabelerde ve şehirlerde kaynıyor. Yoğunluk gece her yerde artar.
- **Gün/gece döngüsü:** bir tam gün 12 dakika. Gündüz çalışır, gece saklanır
  ya da savaşırsın. Işık, sis ve gökyüzü saate göre değişir.

### Hayatta kalma

- **Dört kaynak:** hurda, odun, taş ve lif. Hepsi dünyadan toplanır.
- **Toplama:** bir ağacın, kayanın, çalının ya da varilin yanına gidince
  toplama düğmesi belirir; basılı tutarsın, iş dolunca kaynak kasaya girer.
  Kesilen ağacın yerinde kütük kalır ve bir süre sonra geri büyür.
- **Balta ve kazma** üretip geliştirirsin: balta odun, kazma taş toplamayı
  hızlandırır (odun + hurda + lif ile, 4 seviye).
- **Açlık ve susuzluk** sürekli azalır. Biri sıfırlanırsa can erimeye başlar,
  düşükken yaran da iyileşmez ve yavaşlarsın. Yiyecek çalılardan (meyve),
  enkazdan, zombilerin üstünden ve şehir sandıklarından çıkar.
- **İnşaat topladığın malzemeyle yapılır:** duvar tamamen odundan örülür,
  kuleler hurda + odun ister, ağır yapılar taş da. Hiçbir şey "para" ile
  alınmaz; ne topladıysan onu kurarsın.

### Gece baskınları

- Gün batımında **kampın kokusunu alan zombiler üsse yürümeye başlar.** Bunlar
  bir dalga değil: gece boyunca damla damla gelirler, şafakla birlikte kesilir.
- Baskının büyüklüğü **hayatta kalınan güne** göre artar.
- **Her yedinci gece kanlı ay:** çok daha kalabalık, daha hızlı ve elit dolu.
- Dünyada gezen zombiler bundan ayrıdır; onlar üsse yürümez, kendi bölgelerinde
  dolaşır ve yaklaşırsan saldırır.

### Kamp

- Merkezde **reaktör** var; düşerse oyun biter.
- **Sur hattı iç içe halkalardan oluşur ve yer daraldıkça kendiliğinden
  büyür:** reaktörün etrafında dokunulmaz bir boşluk, sonra avlu (destek
  yapıları), sonra kule kuşağı, sonra sur ve surun dışında tuzak bandı.
  Her kenarın ortasında dört hücrelik geniş bir kapı açık kalır.
- **14 farklı yapı**, hepsi geliştirilebilir (görünümleri de değişir):
  duvar, dikenli tuzak, makineli/top/alev/tesla/nişancı kuleleri, jeneratör,
  cephanelik, tamir istasyonu, tıbbi istasyon, hurda işleyici, kışla.
- **Duvarlar komşularına göre birleşir** (köşe, T ve haç parçaları).
- **Enerji sistemi:** kuleler enerji tüketir, jeneratörler üretir.
- **6 silah**, her biri 5 seviye geliştirilebilir.
- **Karakter gelişimi:** öldürdükçe tecrübe → seviye → yetenek puanı, 12 dal.

### Yoldaşlar

- Kışla kurup dört rolde yoldaş alırsın: muhafız, mühendis, toplayıcı, sağlıkçı.
- **Duruş** (takip et / burayı tut / reaktörü koru / bölgeye saldır / geri çekil
  / serbest karar) ve **görevler** ayrı ayrı ayarlanır. Görevler bir liste değil
  bir küme: tek bir yoldaşa aynı anda savaş + onar + inşa + topla + iyileştir
  verebilirsin. Her rolün her görevde ayrı verimi vardır.
- **Kaynak toplarlar:** yerde ganimet varsa önce onu alır, yoksa ağaç keser,
  taş kırar, enkaz ayıklar. Kasada en az olan kaynağa öncelik verirler.
- **Kendi kendine inşaat:** mimar sana sormadan kampı planlar — enerji açığına
  jeneratör, sur hattındaki deliğe duvar, zayıf yöne kule, kapı önüne tuzak.
  Yer kalmayınca sur hattını bir kademe dışarı taşır ve eski hattın kapılarını
  sonuna kadar açar.
- **Mesafe koruma:** her yoldaşın rolüne göre bir rahat mesafesi vardır. Zombi
  içeri girerse ateş etmeyi kesmeden geri çekilir; histerezis sayesinde bir
  ileri bir geri titremez. Ulaşamadığı bir hedefte takılıp kalmaz — üç saniye
  yaklaşamazsa vazgeçip başka işe geçer.
- **Konuşma baloncukları:** ne yapacağını başının üstünde söyler.
- **Ortak kasa:** senin topladığın da onların getirdiği de aynı kasaya girer.

## Kontroller (dokunmatik)

| Eylem | Nasıl |
|---|---|
| Hareket | Sol yarıya bas ve sürükle (dinamik sanal çubuk) |
| Ateş | Sağ alttaki ATEŞ düğmesi — en yakın hedefe otomatik nişan |
| Şarjör / Atılma / Silah değiştir | ATEŞ'in yanındaki yuvarlak düğmeler |
| Kaynak toplama | Bir ağacın/kayanın/çalının/varilin yanına gidince yeşil toplama düğmesi belirir; basılı tut |
| Kamera | Sağ yarıda sürükle, iki parmakla yakınlaştır |
| İnşa modu | Sağ üstteki "İNŞA MODU" düğmesi |
| Yapı kurma | İnşa modunda alttan yapı seç, ızgaraya dokun (sürükleyerek seri dizebilirsin) |
| Ekip | Sağdaki EKİP düğmesi paneli açar: yoldaş al, emir ver |
| Döndürme | Soldaki YÖN düğmesi yeni yapının yönünü, bir yapı seçiliyken onun yönünü çevirir (duvarlar kendiliğinden hizalanır) |
| Geliştir / onar / sat | Kurulu bir yapıya dokun, sağdaki panelden seç |

## APK nasıl alınır

### GitHub Actions (önerilen — kurulum gerektirmez)
`android/` altında bir değişiklik push edildiğinde
[`.github/workflows/android.yml`](../.github/workflows/android.yml) otomatik
çalışır. İşin **Artifacts** bölümündeki `son-karargah-apk` dosyasını indir,
içindeki `app-debug.apk`'yı telefona kopyalayıp kur ("bilinmeyen kaynaklar"
iznini vermen gerekir). Workflow'u elle de başlatabilirsin (Actions → APK derle
→ Run workflow).

### Yerelde derleme
Android SDK (API 35) ve JDK 17 kuruluysa:

```bash
cd android
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease    # imzasız/debug imzalı sürüm derlemesi
```

### Kendi imzanla sürüm derlemesi (isteğe bağlı)
Depo gizli değişkenlerine (Settings → Secrets → Actions) şunları eklersen
release APK gerçek anahtarınla imzalanır ve güncellemeler üst üste kurulabilir:

| Secret | Açıklama |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 anahtar.jks` çıktısı |
| `KEYSTORE_PASSWORD` | keystore parolası |
| `KEY_ALIAS` | anahtar takma adı |
| `KEY_PASSWORD` | anahtar parolası |

Bunlar tanımlı değilse release derlemesi debug anahtarıyla imzalanır
(kurulur, ama her derlemede anahtar değişebileceği için güncellerken
önce eskisini kaldırman gerekebilir).

## Teknik yapı

```
android/app/src/main/java/com/karargah/survival/
├── GameActivity.java        tek etkinlik, yaşam döngüsü, kayıt
├── GameSurface.java         GLSurfaceView + oyun döngüsü (GL iş parçacığı)
├── engine/                  motor: matris, shader, mesh, kamera, parçacık, ses
│   ├── Renderer3D.java      iki gölgelendirici (statik + kemikli), gökyüzü, sis
│   ├── MeshBuilder.java     prosedürel mesh üretimi (kutu/silindir/küre/...)
│   ├── Particles.java       nokta sprite parçacık sistemi
│   └── Audio.java           tamamen sentezlenen ses (hiç ses dosyası yok)
├── game/                    simülasyon
│   ├── Balance.java         bütün denge verileri tek dosyada
│   ├── GameWorld.java       ana döngü, kuleler, hasar, ekonomi
│   ├── Zombie.java / Player.java / Structure.java / Projectile.java
│   ├── FlowField.java       zombiler için Dijkstra akış alanı
│   ├── PathFinder.java      yoldaşlar için A* (duvarları dolaşır)
│   ├── Npc.java             yoldaş yapay zekâsı: duruş + çoklu görev, iş seçimi
│   ├── BuildPlan.java       şantiye (yoldaşların kurduğu inşa/geliştirme planı)
│   ├── BasePlanner.java     üssü okuyup nereye ne kurulacağına karar veren mimar
│   ├── Advisor.java         üssü değerlendirip öneri veren yoldaş aklı
│   ├── Pickup.java          yere düşen hurda/çekirdek/yiyecek/su
│   ├── WorldGen.java        açık dünya üreteci: biyom, şehir, bina, yoğunluk
│   ├── Harvest.java         kaynak düğümleri: ağaç, kaya, çalı, enkaz
│   ├── Threat.java          gün/geceye bağlı tehdit ve gece baskınları
│   ├── BaseLayout.java      kampın halka düzeni ve genişleme kuralları
│   ├── Models.java          bütün 3B modeller kodla üretilir
│   ├── WorldRenderer.java   sahneyi çizer, iskelet animasyonu
│   └── SaveStore.java       JSON kayıt
└── ui/                      Canvas tabanlı dokunmatik arayüz
    ├── UiKit.java           anlık mod arayüz kiti
    └── HudView.java         HUD, sanal çubuk, inşa çubuğu, menüler
```

Hiç harici bağımlılık yok (`dependencies { }` boş): ne AndroidX, ne bir oyun
motoru, ne de model/ses/doku dosyası. Bütün görseller ve sesler açılışta
koddan üretilir; bu yüzden APK birkaç yüz kilobayt.

## Doğrulama

Oyun mantığı çizimden bağımsız olduğu için CI'da gerçekten test edilebiliyor:

```bash
cd android
./gradlew testDebugUnitTest          # oyun simülasyonu + geometri testleri
python3 tools/check_shaders.py       # GLSL ES 3.00 shader derlemesi
```

- `SimulationTest` oyunu çizimsiz olarak binlerce kare koşturur: gece
  baskınlarının tıkanmadığını, sayıların bozulmadığını (NaN/negatif kaynak),
  inşa–geliştir–sat akışının ve denge tablolarının tutarlılığını doğrular.
- `SurvivalTest` survival çekirdeğini denetler: ağaç kesince odun geliyor mu,
  kesilen düğüm geri büyüyor mu, alet toplamayı hızlandırıyor mu, duvar
  gerçekten odunla mı örülüyor, gece baskını gün batımında başlayıp şafakta
  bitiyor mu, yoldaşlar kaynak topluyor mu.
- `WorldTest` açık dünyayı denetler: dünya deterministik mi, bütün biyomlar
  haritada var mı, şehirlerde bina ve sokak var mı, zombi yoğunluğu şehirlerde
  gerçekten yüksek mi, kampın çevresi temiz mi, üs yer kalmayınca genişliyor mu.
- `M4Test` matris matematiğini doğrular (matrisler bilerek saf Java'dır:
  `android.opengl.Matrix` birim testlerinde boş döndüğü için mesh üretimi test
  edilemezdi; uygulama geliştirme sırasında rastgele girdilerle Android'in
  sürümüyle karşılaştırılıp birebir aynı olduğu doğrulandı).
- `GeometryTest` prosedürel mesh üreticisini denetler: normaller birim
  uzunlukta mı, üçgen sarım yönü doğru mu, kapalı şekillerin yüzleri dışa
  bakıyor mu (ters sarım modelleri içten gösterir).
- `tools/check_shaders.py` gölgelendiricileri `glslangValidator` ile derler;
  shader hataları aksi halde ancak cihazda, oyun açılırken ortaya çıkar.

## Dengeyi değiştirmek

`game/Balance.java` içindeki tablolar oyunun tamamını belirler: yapı
maliyetleri (hurda/odun/taş), canları, hasarları, silah istatistikleri, zombi
türleri, gün uzunluğu, gece baskını büyüklüğü, açlık/susuzluk hızları ve
yetenekler. Tek bir sayıyı değiştirip yeniden derlemek yeterli.
Dünyanın şekli (biyom eşikleri, şehir sıklığı, kaynak bolluğu) `WorldGen.java`
ve `Harvest.java` içindedir.
