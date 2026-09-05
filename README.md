# Metin3 — Üç Krallığın Gölgesi

Metin2'den esinlenen, tarayıcıda çalışan mobil uyumlu 3B rol yapma oyunu.
Kişisel kullanım için geliştirilen bağımsız bir çalışma.

## Çalıştırma

Kurulum ve derleme adımı yok. Depoyu bir statik sunucudan servis edip
`index.html`'i açmak yeterli:

```bash
npm start          # python3 -m http.server 8080
# tarayıcıda: http://localhost:8080
```

Doğrudan `file://` ile açmak çalışmaz — ES modülleri ve import map için
HTTP gerekir.

### Tek dosyalık sürüm

Paylaşmak veya çevrimdışı açmak için her şeyin (three.js dahil) gömülü
olduğu tek bir HTML üretilebilir:

```bash
npm install        # yalnızca esbuild
npm run build
```

`dist/index.html` artık `file://` ile de dahil doğrudan açılabilir.

## Kontroller

| Eylem | Mobil | Masaüstü |
|---|---|---|
| Hareket | Sol yarıya dokun ve sürükle | `WASD` / yön tuşları |
| Koşma | Joystick'i sonuna kadar it | `Shift` |
| Kamera | Sağ yarıya dokun ve sürükle | Fareyle sürükle |
| Yakınlaştırma | İki parmakla makas | Fare tekerleği |
| Saldırı (3'lü zincir) | ⚔ düğmesi | `J` veya `E` |
| Üç Yönlü Kesiş (25 MP) | ✦ düğmesi | `1` |
| Kılıç Fırtınası (35 MP) | ☯ düğmesi | `2` |
| Zıplama | ⤒ düğmesi | `Boşluk` |

## Yapı

```
index.html            giriş noktası + import map
vendor/three/         three.js (r169, depoya gömülü — çevrimdışı çalışsın diye)
src/
  main.js             oyun kurulumu ve ana döngü
  core/
    Engine.js         render, gökyüzü, ışık, kalite kademeleri
    Input.js          sanal joystick, kamera sürükleme, klavye
    Textures.js       prosedürel doku üretimi (canvas 2D)
    GeoUtils.js       geometri birleştirme, dünya ölçekli UV
  world/
    Terrain.js        yükseklik alanı, biyom karışımı, yollar
    Architecture.js   kıvrık çatı, kapı, pagoda, sur gibi yapı parçaları
    Village.js        krallık köyü düzeni
    Nature.js         ağaç/kaya dağıtımı (InstancedMesh)
    Collision.js      uzamsal ızgaralı çarpışma
  entities/
    Warrior.js          prosedürel savaşçı + animasyon poz kütüphanesi
    ModelCharacter.js   dışarıdan yüklenen riglenmiş karakter (glb/gltf/fbx)
    CharacterFactory.js hangisinin kullanılacağına karar verir
    PlayerController.js hareket, çarpışma, kamera, beceriler
    SwordTrail.js       kılıç izi efekti
assets/characters/    kendi karakter modelinizi buraya koyun (README'si var)
  ui/
    HUD.js            çubuklar, mini harita, düğmeler
    KingdomSelect.js  açılış ve krallık seçimi
  data/kingdoms.js    üç krallığın tanımı
```

## Kendi karakter modelinizi kullanma

Oyun varsayılan olarak koda gömülü prosedürel savaşçıyla gelir. Riglenmiş ve
animasyonlu bir model (`.glb`, `.gltf`, `.fbx`) `assets/characters/` altına
konup `warrior.json` ile tanıtılırsa oyuncu karakteri onunla değiştirilir —
kod değişikliği gerekmez. Animasyon klipleri adlarına göre otomatik eşleşir,
eşleşme konsola yazılır ve gerekirse elle bağlanabilir. Ayrıntılar:
[`assets/characters/README.md`](assets/characters/README.md).

Model yoksa, bozuksa ya da kapalıysa oyun prosedürel karaktere döner.

## Varlıklar hakkında

Oyundaki **bütün** modeller, dokular ve animasyonlar çalışma anında kod
içinde üretilir; hiçbir dış varlık dosyası yoktur. Metin2'ye ait hiçbir
model, doku, ses veya animasyon kullanılmamıştır ve kullanılmayacaktır —
bunlar Webzen/Gameforge'un telifli varlıklarıdır. Benzerlik yalnızca
görsel dil (Doğu Asya mimarisi, üç krallık teması) düzeyindedir.

Tek dış bağımlılık [three.js](https://threejs.org) (MIT), `vendor/three/`
altında lisansıyla birlikte gömülüdür.
