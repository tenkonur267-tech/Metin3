# Karakter modeli ekleme

Oyun varsayılan olarak koda gömülü prosedürel savaşçıyı kullanır. Buraya bir
model koyarsanız oyuncu karakteri onunla değiştirilir; hiçbir kod değişikliği
gerekmez.

## Adımlar

1. Riglenmiş ve animasyonlu bir modeli bu klasöre koyun (`.glb`, `.gltf` veya
   `.fbx`).
2. `warrior.json` dosyasında `enabled` değerini `true` yapın ve `file`
   alanına model dosyanızın adını yazın.
3. Oyunu açın ve tarayıcı konsoluna bakın — hangi animasyonun hangi oyun
   durumuna eşlendiği ve eşlenmeyen klipler orada listelenir.

`warrior.json` yoksa, `enabled: false` ise veya model yüklenemezse oyun
prosedürel karaktere döner ve açılmaya devam eder.

## Animasyon eşleştirme

Klipler adlarına bakılarak otomatik eşleşir. Aranan oyun durumları:

| Durum | Klip adında aranan | Zorunlu mu |
|---|---|---|
| `idle` | idle, stand, breathing, rest | evet |
| `walk` | walk, walking | evet |
| `run` | run, sprint, jog | hayır (walk'a düşer) |
| `attack` | attack, slash, swing, strike, cut | hayır |
| `combatIdle` | combatidle, guard, battle, ready | hayır (idle'a düşer) |
| `spin` | spin, whirl, cyclone, sweep | hayır (attack'a düşer) |
| `tripleCut` | combo, triple, flurry, special | hayır (attack'a düşer) |
| `jump` | jump, air, falling, leap | hayır |
| `hit` | hit, hurt, damage, flinch | hayır |
| `die` | die, death, defeat | hayır |

Birden fazla saldırı klibi bulunursa (`Attack1`, `Attack2`, `Attack3`) üçlü
vuruş zincirine sırayla dağıtılır.

Otomatik eşleşme yanlışsa `clips` alanından elle bağlayın:

```json
"clips": { "attack": "Sword_Slash_02", "die": "Character_Death" }
```

## Ayak kaymasını giderme

Yürüyüş klipleri belirli bir hız için üretilir. Karakter yürürken ayakları
kayıyorsa `clipWalkSpeed` / `clipRunSpeed` değerlerini ayarlayın: bunlar
klibin **normal hızda oynatıldığında** karşılık geldiği metre/saniye hızıdır.
Ayaklar ileri kayıyorsa değeri düşürün, geri kayıyorsa yükseltin.

## Kılıç izi efekti

Kılıç izinin çizilmesi için silahın tutulduğu kemiğin adı gerekir:

```json
"weaponBone": "mixamorigRightHand",
"weaponBaseOffset": [0, 0.1, 0],
"weaponTipOffset": [0, 1.2, 0]
```

Kemik adı verilmezse iz efekti kapatılır. Kemik adlarını konsolda görmek için:

```js
window.game.player.model.traverse(o => o.isBone && console.log(o.name))
```

## Lisans

Buraya koyduğunuz modelin kullanım iznine sahip olduğunuzdan emin olun ve
lisansını bu klasöre bir metin dosyası olarak ekleyin. Depoda varsayılan
olarak model bulunmaz.
