# Hazır model paketi

Bu klasöre bir OBJ paketi bırakırsan oyun kodu değiştirmeden onu kullanır.
Klasör boşsa her şey eskisi gibi kodla üretilir — paket **isteğe bağlıdır** ve
**kısmi olabilir**: yalnız `tree.obj` koyarsan yalnız ağaçlar değişir.

## Nereden bulunur

Motor doku okumadığı için (aşağıya bak) low-poly, malzeme renkli paketler
gerekir. En iyi iki kaynak:

| Kaynak | Lisans | Önerilen kitler |
|---|---|---|
| kenney.nl/assets | CC0 (atıf gerekmez) | Nature Kit, Survival Kit, City Kit, Modular Buildings, Car Kit |
| quaternius.com | CC0 | Ultimate Nature, Modular Buildings, Survival |
| poly.pizza | CC0 / CC-BY | tek tek model |
| opengameart.org | karışık — her modelin lisansını ayrı kontrol et | |

İndirdiğin pakette genelde `Models/OBJ/` klasörü olur; gereken o.

## Beklenen dosyalar

| Dosya | Yerine geçtiği model | Önerilen boy (birim ≈ metre) |
|---|---|---|
| `rock.obj` | kaya | ~1,8 çap |
| `tree.obj` | ağaç | ~3,5 yükseklik |
| `barrel.obj` | varil | ~1,2 yükseklik |
| `crate.obj` | sandık | ~1,0 küp |
| `grass.obj` | ot tutamı | ~0,6 yükseklik |
| `building.obj` | şehir ve mekân binası | **tam 1×1×1 birim küp** |

Yanlarına aynı adla `.mtl` koy; yüz renkleri oradaki `Kd` değerinden okunur.

`building.obj` özeldir: çizilirken her binanın boyutuna göre ölçeklendiği için
tam olarak bir birim küp olmalı ve tabanı y=0'da durmalı. Uygun bir kutu yoksa
boş bırak, kodla üretilen bina kalır.

## Modelde aranacaklar

- **Format OBJ + MTL.** Yalnız FBX/GLB/Blend varsa önce dönüştürmek gerekir.
- **Low-poly.** Model başına ~2000 üçgenin altı; karede 200-400 nesne çiziliyor.
- **Y yukarı, taban y=0'da.** Havada asılı ya da yere gömülü olmasın.
- **Ölçek serbest** — çizilirken ayarlanır, ama yukarıdaki boylara yakın olması
  elle ayar gerektirmez.

## Sınırlar

- **Doku yok.** Motorun köşe düzeni konum + normal + köşe rengidir; `.png`
  dokular okunmaz. Dokulu (PBR) paketler düz renkli ama doğru biçimli görünür.
  Bu yüzden vertex/malzeme renkli low-poly paketler doğru sonucu verir.
- Üçgen ve çokgen yüzler, negatif indeksler, `v`/`vn`/`f`/`usemtl`/`mtllib`
  desteklenir. Normal yoksa yüz normali hesaplanır.
- Bozuk ya da okunamayan model sessizce atlanır; oyun yine açılır.

## Lisans

Buraya konan her şeyin lisansı net olmalı (CC0, CC-BY, MIT gibi) ve atıf
gerekiyorsa bu dosyaya yazılmalı. **Şu an klasörde hiçbir üçüncü taraf varlık
yok.**
