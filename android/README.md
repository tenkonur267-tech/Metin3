# Son Karargâh — 3B mobil hayatta kalma / üs savunma oyunu

Android için yazılmış **gerçek bir native oyun**: tarayıcı yok, WebView yok,
oyun motoru bağımlılığı yok. Tüm 3B motor (OpenGL ES 3.0), ses sentezi,
fizik ve arayüz bu depodaki Java kodudur. Çıktı doğrudan kurulabilir bir APK'dır.

## Oyun

Haritanın ortasındaki **reaktörü** sonsuz zombi dalgalarına karşı savun.
Her dalga arasında bir **hazırlık aşaması** var: bu sürede üssünü kurar,
yapılarını geliştirir, silah alır ve yeteneklerine puan dağıtırsın.

- **Dalga döngüsü:** hazırlık → dalga → temizlendi → hazırlık... Sonsuza kadar.
  Her 5. dalgada **Mutant Dev** gelir ve enerji çekirdeği bırakır.
- **Duvarlar komşularına göre birleşir** (köşe, T ve haç parçaları) — sıraladığın
  duvarlar boşluksuz tek bir sur gibi durur.
- **12 farklı yapı**, hepsi 1→5 seviye geliştirilebilir (görünümleri de değişir:
  kule namluları çoğalır, zırh plakası ve enerji hatları eklenir, yapı irileşir):
  duvar, dikenli tuzak, makineli/top/alev/tesla/nişancı kuleleri, jeneratör,
  cephanelik, tamir istasyonu, tıbbi istasyon, hurda toplayıcı.
- **Enerji sistemi:** kuleler enerji tüketir, jeneratörler üretir. Açık varsa
  bütün kulelerin atış hızı düşer — üssü planlamak gerekir.
- **6 silah** (tabanca, hafif makineli, pompalı, saldırı tüfeği, keskin nişancı,
  roketatar), her biri 5 seviye geliştirilebilir.
- **Karakter gelişimi:** öldürdükçe tecrübe → seviye → yetenek puanı.
  12 yetenek dalı (can, zırh, hız, hasar, şarjör, kritik, mühendislik,
  usta tamirci, hurdacı, kan emici, kaçış ustası, yenilenme).
- **6 zombi türü:** yürüyen, koşucu, sürüngen, tüküren, kaba ve dev boss;
  ayrıca dalga ilerledikçe çıkan güçlendirilmiş (elit) türevler.
- **Akıllı yol bulma:** zombiler Dijkstra tabanlı akış alanıyla en ucuz yolu
  arar; yol kapalıysa en zayıf duvarı kırmayı seçer. Yani labirent kurabilirsin.
- **Yoldaş NPC'ler:** Kışla kurup dört rolde yoldaş alırsın — muhafız, mühendis,
  toplayıcı, sağlıkçı. EKİP panelinde iki ayrı şey ayarlanır:
  **duruş** (takip et / burayı tut / reaktörü koru / bölgeye saldır / geri çekil)
  ve **görevler**. Görevler bir liste değil bir küme: tek bir yoldaşa aynı anda
  savaş + onar + inşa + topla + iyileştir verebilirsin. Hangisinin daha acil
  olduğuna kendisi karar verir, uzun süre aynı işte kalırsa sırayı diğerine
  bırakır. Her rolün her görevde ayrı verimi vardır (mühendis inşada %100,
  muhafız %50 gibi) ve panelde gösterilir.
- **İnşaatçı yoldaşlar:** İnşa modunda PLAN düğmesini açıp zemine dokununca
  şantiye bırakırsın; İNŞA görevi olan yoldaş gider, ortak kasadan ödeyip
  yapıyı kurar (ilerleme zeminde çubukla görünür). OTO açıkken yıkılan her
  yapı için kendiliğinden plan açılır — ekip üssü sen uğraşmadan yeniden diker.
- **Ortak kasa:** Hurda ve çekirdek tek kasada. Senin topladığın, toplayıcının
  getirdiği, dalga ödülleri hep oraya girer; inşaat, geliştirme ve yoldaş
  ücretleri hep oradan ödenir. Üst çubukta ekibin katkısı ayrıca gösterilir.
- Emir dışında da akıllıdırlar: A* ile duvarları dolaşır (takılmazlar), ateş
  hattı kapalıysa ateş etmez, canları azalınca kendiliğinden geri çekilip
  iyileşir ve üssün durumunu değerlendirip öneride bulunurlar ("enerji açığın
  var", "batı taraf savunmasız", "şantiyeler bekliyor, kimsede inşa görevi yok").
- **Yere düşen ganimet:** Ölen zombiler hurda (boss ayrıca enerji çekirdeği)
  düşürür. Yaklaşınca kendiliğinden çekilir, toplayıcı yoldaş senin için
  toplar, dalga bitince sahada kalanlar otomatik toplanır.
- **Kayıt:** oyun otomatik kaydedilir, ana menüden "Devam Et" ile sürdürülür.

## Kontroller (dokunmatik)

| Eylem | Nasıl |
|---|---|
| Hareket | Sol yarıya bas ve sürükle (dinamik sanal çubuk) |
| Ateş | Sağ alttaki ATEŞ düğmesi — en yakın hedefe otomatik nişan |
| Şarjör / Atılma / Silah değiştir | ATEŞ'in yanındaki yuvarlak düğmeler |
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
│   ├── BuildPlan.java       şantiye (yoldaşların kurduğu inşa planı)
│   ├── Advisor.java         üssü değerlendirip öneri veren yoldaş aklı
│   ├── Pickup.java          yere düşen hurda/çekirdek
│   ├── WaveManager.java     dalga kadroları ve hazırlık süresi
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

- `SimulationTest` oyunu çizimsiz olarak binlerce kare koşturur: dalgaların
  tıkanmadığını, sayıların bozulmadığını (NaN/negatif kaynak), inşa–geliştir–sat
  akışının ve denge tablolarının tutarlılığını doğrular.
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
maliyetleri/canları/hasarları, silah istatistikleri, zombi türleri, dalga
bütçesi ve yetenekler. Tek bir sayıyı değiştirip yeniden derlemek yeterli.
