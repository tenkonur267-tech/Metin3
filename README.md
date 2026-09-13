[![Godot Engine](https://img.shields.io/badge/Godot-4.6+-blue?logo=godot-engine)](https://godotengine.org/)
[![License: MIT](https://img.shields.io/badge/license-MIT-green.svg)](https://opensource.org/licenses/MIT)
[![Platform](https://img.shields.io/badge/platform-Android%20%7C%20Web%20%7C%20Desktop-informational.svg)](MOBILE.md)

# Metin3

**Açık kaynaklı, mobil öncelikli MMORPG.** Godot 4 ile yazılıyor; ilk hedef
Android, sonrasında iOS.

Proje [**godot-tiny-mmo**](https://github.com/SlayHorizon/godot-tiny-mmo)
(MIT, © 2025-2026 slayhorizon) fork'u olarak başladı. Upstream masaüstü için
tasarlanmış ama gerçek bir MMO mimarisi ve hazır dokunmatik altyapısıyla
geliyordu — Metin3 bunu telefona taşıyor.

> **Metin2 ile ilişkisi yok.** Metin2 ve tüm görsel/işitsel varlıkları
> Webzen telifindedir. Bu depoda Metin2'den alınmış hiçbir varlık yok ve
> olmayacak. Ayrıntı: [MOBILE.md](MOBILE.md#5-hukuki-not).

---

## Mimari

Upstream'den devralınan gerçek MMO sunucu yapısı:

- **Gateway server** — kimlik doğrulama ve yönlendirme
- **Master server** — orkestrasyon, hesap yönetimi, gateway ↔ world köprüsü
- **World server** — eşzamanlı harita ve instance'ları barındırır; oyunun geçtiği yer

Ağ katmanı Godot'un `MultiplayerSynchronizer/Spawner`'ına dayanmıyor; ID
tabanlı, byte-paketli (`PackedByteArray`) kendi protokolü var. Büyük
haritalarda grid tabanlı interest management (AOI) ile filtreleme yapılıyor.

Client ve sunucular **tek depoda**, ayrı export presetleriyle.

---

## Durum

| | |
|---|---|
| Çalışan | Kimlik doğrulama, hesap/karakter oluşturma, 3 sınıf (Knight, Rogue, Wizard), savaş, lonca, arkadaş listesi, instance'lı sohbet, haritalar arası geçiş, sunucu taraflı NPC, SQLite kalıcılık, web tabanlı admin paneli |
| Mobilde hazır | Twin-stick dokunmatik kontrol, girdi tipi otomatik algılama, dokunmayla etkileşim, güvenli alan (çentik/gesture bar), mobil en-boy oranı, Android export preseti |
| Eksik | HUD ölçeklendirme, giriş ekranı güvenli alanı, Metin3 markası (logo/metinler), gerçek cihazda performans profili, entity interpolation |

Ayrıntılı mobil durumu ve yol haritası: **[MOBILE.md](MOBILE.md)**

---

## Geliştirmeye başlama

### Masaüstünde (tam yığın, tek Godot)

1. Projeyi **Godot 4.6.3** ile aç.
2. `Debug → Customizable Run Instance...`
3. **Multiple Instances**'ı aç, sayıyı **4 veya daha fazla** yap.
4. **Feature Tags** altında:
   - Tam **bir** `gateway-server`
   - Tam **bir** `master-server`
   - Tam **bir** `world-server`
   - **En az bir** `client`
5. (İsteğe bağlı) **Launch Arguments**: sunucular için `--headless`,
   farklı config için `--config=dosya_yolu.cfg`.
6. F5.

### Android'de

`MOBILE.md` → [Android build alma](MOBILE.md#4-android-build-alma).
Özet:

```bash
godot --headless --path . --export-debug "Android" exports/android/metin3.apk
adb install -r exports/android/metin3.apk
```

Telefon ayrı bir cihaz olduğu için `127.0.0.1` çalışmaz —
`adb reverse tcp:8088 tcp:8088` kullan ya da `metin3/network/gateway_url`
proje ayarını geliştirme makinenin LAN IP'sine çevir.

---

## Upstream ile senkron kalma

Depo upstream'i `upstream` remote'u olarak taşıyor, tüm geçmişiyle:

```bash
git remote add upstream https://github.com/SlayHorizon/godot-tiny-mmo.git
git fetch upstream
git merge upstream/main
```

Mobil değişiklikler bilerek dar tutuldu (yeni bir autoload, HUD kökünde bir
inset, proje ayarlarında `.mobile` override'ları) — böylece upstream'den gelen
güncellemeler çakışmadan birleşiyor.

---

## Katkı

Fork'layıp PR açabilirsiniz. Tartışma için
[Issue](https://github.com/tenkonur267-tech/Metin3/issues) açın.

---

## Teşekkür

Upstream projeyi ve onu mümkün kılanları:

- [**@SlayHorizon**](https://github.com/SlayHorizon) — godot-tiny-mmo'nun yazarı
- **Haritalar**: [@higaslk](https://github.com/higaslk)
- Yardım ve geri bildirim: [@Jackiefrost](https://github.com/Jackietkfrost),
  [@d-Cadrius](https://github.com/d-Cadrius) ve isimsiz katkıcılar
- [@Anokolisa](https://anokolisa.itch.io/dungeon-crawler-pixel-art-asset-pack) —
  varlık paketini açık kaynak projede kullanma izni için

## Lisans

Kaynak kod [MIT Lisansı](LICENSE) altında, telif upstream yazarına ait
(© 2025-2026 slayhorizon). Ticari kullanım dahil serbest; tek şart telif
bildiriminin korunması.
