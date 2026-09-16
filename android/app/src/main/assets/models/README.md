# Hazır model paketi

Bu klasöre bir OBJ paketi bırakırsan oyun kodu değiştirmeden onu kullanır.
Klasör boşsa her şey eskisi gibi kodla üretilir — paket **isteğe bağlıdır** ve
kısmi olabilir (yalnız ağaç koyarsan yalnız ağaç değişir).

## Beklenen dosyalar

| Dosya | Yerine geçtiği model | Önerilen boy (birim) |
|---|---|---|
| `rock.obj` | kaya | ~1.8 çap |
| `tree.obj` | ağaç | ~3.5 yükseklik |
| `barrel.obj` | varil | ~1.2 yükseklik |
| `crate.obj` | sandık | ~1.0 küp |
| `grass.obj` | ot tutamı | ~0.6 yükseklik |
| `building.obj` | şehir/mekân binası | **1x1x1 birim küp**, tabanı y=0'da |

`building.obj` özel: çizilirken her binanın boyutuna göre ölçeklenir, bu yüzden
tam olarak bir birim küp olmalı ve tabanı y=0'da durmalı.

Yanlarına aynı adla `.mtl` koyarsan yüz renkleri oradan (`Kd`) okunur.

## Sınırlar

- **Doku yok.** Motorun köşe düzeni konum + normal + köşe rengidir; `.png`
  dokular okunmaz. Dokulu paketler düz renkli ama doğru biçimli görünür.
  Bu yüzden low-poly / vertex-colored paketler en iyi sonucu verir.
- Üçgen ve çokgen yüzler, negatif indeksler, `v`/`vn`/`f`/`usemtl`/`mtllib`
  desteklenir. Normal yoksa yüz normali hesaplanır.
- Bozuk ya da okunamayan bir model sessizce atlanır; oyun yine açılır.

## Lisans

Buraya koyduğun her şeyin lisansını sen belirlersin. Depoya eklenecekse
lisansı net olmalı (CC0, CC-BY, MIT gibi) ve atıf gerekiyorsa bu dosyaya
yazılmalı. Şu an klasörde hiçbir üçüncü taraf varlık yok.
