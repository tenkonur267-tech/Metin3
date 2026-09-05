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

## İki tür model desteği

`warrior.json` içindeki `kind` alanı hangi yolun kullanılacağını belirler:

| `kind` | Ne zaman | Hareket nereden gelir |
|---|---|---|
| `animated` (varsayılan) | Model kendi animasyon kliplerini getiriyorsa | Modelin klipleri |
| `rigged` | Model yalnızca deri ve iskelet getiriyorsa | Oyunun poz kütüphanesi |

`rigged` yolunda karakterin üstüne zırh takılabilir: model çıplak taban gövde
olarak kalır, ekipman kemiklere ayrı parçalar olarak eklenir.

### Zırh nasıl takılıyor

Zırh parçaları `src/entities/ArmorSet.js` içinde prosedürel üretiliyor ve
krallık paletine göre boyanıyor. Ölçüler kemiklerden okunuyor (gövde boyu,
omuz genişliği, uzuv uzunlukları), böylece farklı boy ve orandaki riglere
aynı set oturuyor.

Parçalar **karakter uzayında** yazılıyor: Y yukarı, Z ileri, X sağ.
Kemiklerin yerel eksen düzeni rigden rige değiştiği için `equip()` parçayı
kemiğin dinlenme yönelimiyle ters döndürerek takıyor; parça tasarlanırken
kemiğin eksenlerini bilmek gerekmiyor, animasyonda yine kemiği takip ediyor.

Slotlar: `chest`, `pauldronL/R`, `bracerL/R`, `thighGuardL/R`, `greaveL/R`,
`bootL/R`, `tassets`, `helmet`, `cape`, `sword`. Tek tek takılıp
çıkarılabiliyor (`equip` / `unequip`), ileride envanterden gelen ekipmana
bağlanabilir.

### Aktarım nasıl çalışıyor

İki iskeletin dinlenme pozları ve kemik eksen düzenleri farklı olduğu için
açılar doğrudan kopyalanamaz. Bunun yerine prosedürel savaşçı görünmez bir
sürücü olarak çalıştırılır, her kemik için iki iskeletin dinlenme
yönelimleri arasındaki fark bir kez ölçülür ve her karede sürücünün ulaştığı
yönelim bu farkla hedefe taşınır. Yaklaşım kemik adlandırmasından ve eksen
düzeninden bağımsızdır; farklı bir rig için yalnızca `boneMap` gerekebilir.

## Depodaki modeller

`knight.glb` — KayKit Adventurers Character Pack, Kay Lousberg
(www.kaylousberg.com), **CC0 1.0** (kamu malı, atıf gerekmez). Lisans metni:
`knight-LICENSE.txt`.

76 animasyon içeriyor; oyunda kullanılan eşleme `warrior.json` içinde açıkça
yazılı. Çift el kılıç modelin içinde geliyor, kalkanlar ve tek el kılıç
`hideNodes` ile gizleniyor.

`base_male.glb` — çıplak taban gövde, 56 kemikli insan rigi, animasyonsuz.
Orange Juice Games / BoQsc reupload, **CC0 1.0**. Lisans metni:
`base_male-LICENSE.txt`. Zırh giydirilebilen modüler karakter için taban
budur; ayarları `base_male.json` içinde hazır duruyor.

Prosedürel savaşçıya dönmek için `warrior.json` içinde `"enabled": false`
yapmanız yeterli.

## Kendi modeliniz için lisans

Buraya koyduğunuz modelin kullanım iznine sahip olduğunuzdan emin olun ve
lisansını bu klasöre bir metin dosyası olarak ekleyin.
