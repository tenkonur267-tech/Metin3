# Metin3 — mobil (Android) kılavuzu

Metin3, [godot-tiny-mmo](https://github.com/SlayHorizon/godot-tiny-mmo) (MIT)
üzerine kurulu. Upstream masaüstü için yazılmış ama dokunmatik altyapısı
hazır geliyordu; bu dosya **neyin zaten hazır olduğunu**, **bu fork'ta neyin
mobil için değiştirildiğini** ve **sırada ne olduğunu** anlatır.

---

## 1. Upstream'den hazır gelenler

Bunları yeniden yazmaya gerek yok:

| Özellik | Yer |
|---|---|
| Twin-stick dokunmatik kontrol (sol = hareket, sağ = nişan/saldırı) | `source/client/ui/hud/touch_stick/`, `source/client/ui/shared/touch_stick.gd` |
| Sabit / dinamik stick modu, ayarlanabilir deadzone | `twin_sticks.gd` + ayarlar menüsü (`touch` bölümü) |
| Girdi tipi otomatik algılama (klavye / gamepad / dokunmatik) | `source/client/local_player/input_component.gd` |
| Dokunmayla NPC, sandık, kapı etkileşimi | `clickable_area.gd`, `interactable.gd`, `dungeon_master.gd` |
| Mobilde hafifletilmiş hava efektleri | `weather_layer.gd` |
| `gl_compatibility` mobil render yolu, ETC2/ASTC doku sıkıştırma | `project.godot` |
| Mobilde gizlenen "Çıkış" butonu (telefonda anlamsız) | `gateway.gd` |
| Android release iş akışı (keystore secret'ına bağlı) | `.github/workflows/release.yml` |

---

## 2. Bu fork'ta mobil için değişenler

### Güvenli alan (çentik / gesture bar)

Upstream'de **hiç yoktu**, ve bu modern telefonlarda gerçek bir hata: Android 15
(targetSdk 35) her uygulamayı kenardan kenara (edge-to-edge) çiziyor ve devre
dışı bırakma seçeneğini artık dinlemiyor. Köşelere yaslanmış HUD rayları
durum çubuğunun, çentiğin veya gesture bar'ın altında kalıyordu.

- `source/client/autoload/device.gd` — `Device` autoload'u. `DisplayServer.get_display_safe_area()`
  okur, ekran pikselinden canvas birimine çevirir, `safe_area_changed` sinyali
  yayar. Masaüstü ve web'de sıfır döner, yani çağıran tarafın platform dalı yazmasına gerek yok.
- `source/client/ui/hud/hud.gd` — HUD kökü kendini bu kadar içeri alır.
  HUD'un tüm çocukları köke anchor'lı olduğu için raylar, stickler ve barlar
  birlikte içeri kayar. Ekran döndürmede yeniden uygulanır.
- `export_presets.cfg` — `screen/edge_to_edge=true`. Artık dürüst bir beyan,
  çünkü UI çentiği hesaba katıyor.

### En-boy oranı

Telefonlar 16:9 değil. Godot'un varsayılan `keep` davranışı 20:9 bir panelde
ekranın beşte birini siyah bant olarak harcıyordu.

- `project.godot` → `window/stretch/aspect.mobile="expand"`. Sadece mobil
  override — masaüstü kadrajı aynen korunuyor.
- `window/handheld/orientation=4` (`SCREEN_SENSOR_LANDSCAPE`): oyun geniş
  kadraj, ama cihaz hangi yöne çevrilirse çevrilsin doğru duruyor — yani
  çentik oyuncunun başparmağının olduğu tarafa denk gelmiyor.

### Android export ayarları

| Ayar | Önce | Sonra | Neden |
|---|---|---|---|
| `runnable` | `false` | `true` | Tek tıkla telefona deploy |
| `screen/edge_to_edge` | `false` | `true` | Android 15 zaten zorluyor; UI artık hazır |
| `permissions/access_network_state` | `false` | `true` | "Bağlantı yok" ile "sunucu kapalı" ayrımı |
| `version/name` | `""` | `"0.28.0"` | Boş olursa Play Console yüklemeyi reddediyor |
| `package/name` | `""` | `"Metin3"` | Uygulama adı |
| `package/unique_name` | `com.ekoniastudio.ekonia` | `com.metin3.game` | Kendi paket kimliğimiz |

Mimari `arm64-v8a` tek başına bırakıldı — Play Store zaten 64-bit şartı
koşuyor, `armeabi-v7a` eklemek APK'yı büyütmekten başka işe yaramaz.

### Sunucu adresi

Upstream release build'leri `https://ws.ekoniaonline.com` adresine sabit
bağlanıyordu — bizim sunucumuz değil. `GatewayAPI.base_url()` artık
`metin3/network/gateway_url` proje ayarını okuyor. Ayar **kasten boş**:
sunucumuz yayına girene kadar paketlenmiş build'ler de localhost'a düşer,
yani sessizce başkasının sunucusuna bağlanmak yerine yüksek sesle ve yerelde
başarısız olur.

---

## 3. Android build alma

### Gereksinimler

- Godot **4.6.3-stable** (`.github/workflows/release.yml` içindeki `GODOT_REF` ile aynı)
- Godot export template'leri (aynı sürüm)
- JDK 17 + Android SDK (Godot: `Editor → Editor Settings → Export → Android`)
- Bir debug keystore (Godot otomatik üretir) — release için kendi keystore'unuz

### Editörden

1. Projeyi Godot 4.6.3 ile aç.
2. `Project → Export → Android` presetini seç.
3. Telefonu USB hata ayıklama açık şekilde bağla, editörün sağ üstündeki
   **Remote Deploy** (telefon ikonu) ile çalıştır. Preset artık `runnable=true`.

### Komut satırından

```bash
godot --headless --path . --export-debug "Android" exports/android/metin3.apk
adb install -r exports/android/metin3.apk
```

Release APK için keystore ortam değişkenlerini ver:

```bash
export GODOT_ANDROID_KEYSTORE_RELEASE_PATH=/yol/release.keystore
export GODOT_ANDROID_KEYSTORE_RELEASE_USER=<alias>
export GODOT_ANDROID_KEYSTORE_RELEASE_PASSWORD=<parola>
godot --headless --path . --export-release "Android" exports/android/metin3.apk
```

### Sunucuyu yerelde çalıştırma

Client varsayılan olarak `http://127.0.0.1:8088` arar. Telefon ayrı bir cihaz
olduğu için localhost işe yaramaz — ya `adb reverse tcp:8088 tcp:8088` kullan,
ya da `metin3/network/gateway_url` ayarını geliştirme makinenin LAN IP'sine
çevir.

### CI

`.github/workflows/release.yml` bir `v*` tag'i push edildiğinde çalışır.
Android adımları `ANDROID_KEYSTORE_BASE64` secret'ına bağlı; itch.io yayını
ise `ITCH_GAME` repository variable'ına. İkisi de tanımlı değilse ilgili adım
atlanır, build kırılmaz.

---

## 4. Sırada ne var

Öncelik sırasıyla:

1. **HUD ölçeklendirme.** `Device.suggested_ui_scale` hesaplanıyor ama
   **uygulanmıyor**. Sebebi tasarımsal: `canvas_items` stretch modunda canvas'ı
   ölçeklemek dünyayı da büyütür, yani görüş alanını daraltır — bu bir düzen
   kararı değil, bir denge kararı. Doğru çözüm HUD temasını ayrı ölçeklemek
   (`source/client/ui/themes/theme_horizon.tres`), dünyayı değil.
2. **Giriş ekranı güvenli alanı.** `gateway.tscn` kökü insetlenmedi: altındaki
   `BackgroundRect` tam ekran anchor'lı, insetlense çentik bölgesinde boyasız
   şerit kalırdı. Ana panel ortalanmış olduğu için zaten çentikten uzak, ama
   köşedeki sürüm etiketi ve bağlantı butonları ayrı ele alınmalı.
3. **Marka.** Kod tarafı (paket kimliği, feature tag, build çıktıları, kullanıcı
   veri dizini) Metin3'e geçti. **Görünen** metinler ve görseller hâlâ upstream'in:
   `gateway.tscn` başlıkları, `data/translations/translations.csv`,
   `help_menu.gd` içindeki Discord/web bağlantıları, `assets/project_icon/`
   içindeki logo. Bunlar için önce Metin3'ün kendi logosu/sitesi/Discord'u lazım.
4. **Performans profili.** Orta segment bir Android cihazda kare süresi ölçülmedi.
   Şüpheli yerler: kalabalık haritalarda oyuncu sayısı, `weather_layer` partikülleri,
   HUD'un her karede yeniden çizilen parçaları.
5. **Dokunmatik ergonomi.** Stickler ekran kenarına ne kadar yakın? Menü
   butonları başparmak erişiminde mi? Gerçek cihazda test edilmeli.

---

## 5. Hukuki not

**Metin2 telifi Webzen'e aittir.** Bu depodaki hiçbir şey Metin2'den alınmadı
ve alınmamalı: ne model, ne doku, ne ses, ne harita, ne de metin. Oyun
mekanikleri (tür olarak) taklit edilebilir; sanat varlıkları, isimler ve
logolar edilemez. Google Play ve App Store bu tür ihlallerde uygulamayı
yayından kaldırır.

Kod tabanı MIT lisanslı ([LICENSE](LICENSE), telif upstream yazarına ait) —
ticari kullanım dahil serbest, tek şart telif bildiriminin korunması.
