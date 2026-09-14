-- m3gg - GameGuardian icin deger tabanli farm araci
--
-- Dokunma gondermeden, yalnizca bellekteki degerleri okuyup yazarak calisan
-- bir otomasyon kurar. Uc asama:
--
--   1. ARA    - bir degeri (can, mana, altin, koordinat) daraltarak bul
--   2. KAYDET - bulunan adresi "bolge adi + offset" olarak kalici hale getir
--   3. FARM   - kayitli girisleri kosullu olarak periyodik yaz
--
-- Ikinci asama isin can alici noktasi: ham adres oyun her acildiginda degisir.
-- Adresi iceren bellek bolgesinin adini ve o bolgenin basindan uzakligini
-- saklarsak bir sonraki acilista yeniden cozebiliriz. Bu, Cheat Engine'deki
-- "static offset" fikrinin GG karsiligidir.

local VERSION = "1.1"
local STORE_DIR = (gg.EXT_STORAGE or "/sdcard") .. "/m3gg"
local STORE_FILE = STORE_DIR .. "/offsets.lua"

local TYPES = {
  { label = "DWORD  (32-bit tamsayi)", t = gg.TYPE_DWORD },
  { label = "FLOAT  (32-bit ondalik)", t = gg.TYPE_FLOAT },
  { label = "QWORD  (64-bit tamsayi)", t = gg.TYPE_QWORD },
  { label = "DOUBLE (64-bit ondalik)", t = gg.TYPE_DOUBLE },
  { label = "WORD   (16-bit tamsayi)", t = gg.TYPE_WORD },
  { label = "BYTE   (8-bit tamsayi)",  t = gg.TYPE_BYTE },
}

local TYPE_NAMES = {
  [gg.TYPE_DWORD] = "DWORD", [gg.TYPE_FLOAT] = "FLOAT",
  [gg.TYPE_QWORD] = "QWORD", [gg.TYPE_DOUBLE] = "DOUBLE",
  [gg.TYPE_WORD] = "WORD",   [gg.TYPE_BYTE] = "BYTE",
}

-- Oyun durumu neredeyse her zaman bu bolgelerdedir. Bayraklar ayri bitler
-- oldugu icin toplama, bitwise OR ile ayni sonucu verir ve GG'nin Lua
-- surumunden bagimsiz calisir.
local DEFAULT_RANGES = gg.REGION_C_ALLOC + gg.REGION_ANONYMOUS
                     + gg.REGION_C_BSS + gg.REGION_C_DATA + gg.REGION_C_HEAP

local entries = {}
local searchType = gg.TYPE_DWORD
local searchIsFuzzy = false

--=========================================================================
-- Kalici depolama
--=========================================================================

local function saveEntries()
  os.execute('mkdir -p "' .. STORE_DIR .. '"')
  local fh, err = io.open(STORE_FILE, "w")
  if not fh then
    gg.alert("Kaydedilemedi: " .. tostring(err))
    return false
  end
  fh:write("-- m3gg offset tablosu\nreturn {\n")
  for _, e in ipairs(entries) do
    fh:write(string.format(
      "  { name = %q, typeId = %d, region = %q, offset = %d, absolute = %s,"
      .. " value = %q, freeze = %s, below = %q },\n",
      e.name, e.typeId, e.region, e.offset, tostring(e.absolute and true or false),
      tostring(e.value or ""), tostring(e.freeze and true or false),
      tostring(e.below or "")))
  end
  fh:write("}\n")
  fh:close()
  return true
end

local function loadEntries()
  local chunk = loadfile(STORE_FILE)
  if not chunk then return {} end
  local ok, data = pcall(chunk)
  if ok and type(data) == "table" then return data end
  return {}
end

--=========================================================================
-- Bolge / offset cozumleme
--=========================================================================

-- Ayni kutuphane birden fazla kez eslenir (r--, r-x, rw-); taban her zaman
-- en dusuk baslangictir.
local function regionBase(name)
  local best = nil
  for _, r in ipairs(gg.getRangesList(name) or {}) do
    if r.name == name and (best == nil or r.start < best) then
      best = r.start
    end
  end
  return best
end

local function rangesSnapshot()
  return gg.getRangesList() or {}
end

local function regionForAddress(addr, ranges)
  for _, r in ipairs(ranges) do
    if addr >= r.start and addr < r["end"] then return r end
  end
  return nil
end

-- Anonim bolgelerin adi yoktur ve baslangiclari her acilista degisir, bu
-- yuzden offset olarak saklanamazlar; onlari mutlak adres olarak tutup
-- yalnizca bu oturumda gecerli sayiyoruz.
local function resolveEntry(e)
  if e.absolute then return e.offset end
  local base = regionBase(e.region)
  if not base then return nil end
  return base + e.offset
end

local function readEntry(e)
  local addr = resolveEntry(e)
  if not addr then return nil, "bolge yuklu degil" end
  local got = gg.getValues({ { address = addr, flags = e.typeId } })
  if not got or not got[1] then return nil, "okunamadi" end
  return got[1].value, addr
end

--=========================================================================
-- Yardimcilar
--=========================================================================

local function fmtAddr(a) return string.format("%X", a) end

local function pickType(message)
  local labels = {}
  for i, t in ipairs(TYPES) do labels[i] = t.label end
  local i = gg.choice(labels, nil, message or "Deger tipi")
  if not i then return nil end
  return TYPES[i].t
end

local function ask(title, default, kind)
  local r = gg.prompt({ title }, { default }, { kind or "number" })
  if not r then return nil end
  return r[1]
end

--=========================================================================
-- Sonuc listeleme ve kaydetme
--=========================================================================

local function listResults()
  local res = gg.getResults(30)
  if not res or #res == 0 then
    gg.alert("Sonuc yok.")
    return
  end
  local ranges = rangesSnapshot()
  local lines = {}
  for i, r in ipairs(res) do
    local reg = regionForAddress(r.address, ranges)
    local rname = (reg and reg.name ~= "" and reg.name) or "anonim"
    lines[i] = string.format("%s = %s  [%s]", fmtAddr(r.address),
                             tostring(r.value), rname)
  end
  gg.alert(table.concat(lines, "\n"))
end

local function saveFromResults()
  local res = gg.getResults(30)
  if not res or #res == 0 then
    gg.alert("Once bir arama yapin.")
    return
  end

  local ranges = rangesSnapshot()
  local labels = {}
  for i, r in ipairs(res) do
    local reg = regionForAddress(r.address, ranges)
    labels[i] = string.format("%s = %s  %s", fmtAddr(r.address),
      tostring(r.value), (reg and reg.name ~= "" and reg.name) or "(anonim)")
  end
  local pick = gg.choice(labels, nil, "Kaydedilecek adresi secin")
  if not pick then return end

  local r = res[pick]
  local reg = regionForAddress(r.address, ranges)
  local named = reg ~= nil and reg.name ~= ""
  if not named then
    gg.alert("Bu adres isimsiz (anonim) bir bolgede.\n\n"
      .. "Anonim bolgelerin baslangici her acilista degisir, bu yuzden "
      .. "offset olarak saklanamaz. Kayit yalnizca bu oturum icin gecerli "
      .. "olacak.\n\nKalici bir giris icin degeri bir kutuphane bolgesinde "
      .. "(libX.so) bulmayi deneyin.")
  end

  local name = ask("Giris adi (ornek: hp)", "", "text")
  if not name or name == "" then return end

  local base = named and regionBase(reg.name) or nil
  entries[#entries + 1] = {
    name = name,
    typeId = searchType,
    region = named and reg.name or "",
    offset = base and (r.address - base) or r.address,
    absolute = not named,
    value = tostring(r.value),
    freeze = false,
    below = "",
  }
  if saveEntries() then gg.toast("'" .. name .. "' kaydedildi") end
end

--=========================================================================
-- 1) Deger arama
--=========================================================================

local function refineMenu()
  while true do
    local n = gg.getResultsCount()
    local items
    if searchIsFuzzy then
      items = { "Azaldi", "Artti", "Degismedi", "Degisti",
                "Kesin degerle daralt", "Sonuclari listele",
                "Bu sonucu kaydet", "Bitir" }
    else
      items = { "Yeni degerle daralt", "Sonuclari listele",
                "Bu sonucu kaydet", "Bitir" }
    end
    local c = gg.choice(items, nil,
      string.format("%d aday\n\nOyunda degeri degistirip daraltin.", n))
    if not c then return end

    if searchIsFuzzy then
      if c == 1 then gg.refineFuzzy("0", gg.SIGN_FUZZY_LESS, searchType)
      elseif c == 2 then gg.refineFuzzy("0", gg.SIGN_FUZZY_GREATER, searchType)
      elseif c == 3 then gg.refineFuzzy("0", gg.SIGN_FUZZY_EQUAL, searchType)
      elseif c == 4 then gg.refineFuzzy("0", gg.SIGN_FUZZY_NOT_EQUAL, searchType)
      elseif c == 5 then
        local v = ask("Kesin deger", "")
        if v and v ~= "" then
          -- Fuzzy sonuclarini kesin degere daraltmak icin fuzzy modundan
          -- cikmak gerekir; GG bunu normal refine ile yapar.
          gg.refineNumber(v, searchType)
          searchIsFuzzy = false
        end
      elseif c == 6 then listResults()
      elseif c == 7 then saveFromResults()
      else return end
    else
      if c == 1 then
        local v = ask("Yeni deger", "")
        if v and v ~= "" then gg.refineNumber(v, searchType) end
      elseif c == 2 then listResults()
      elseif c == 3 then saveFromResults()
      else return end
    end
  end
end

local function searchFlow()
  local t = pickType("Aranacak degerin tipi")
  if not t then return end
  searchType = t

  local mode = gg.choice({
    "Degeri biliyorum (ornek: can 1500)",
    "Degeri bilmiyorum (sadece bar var)",
  }, nil, "Arama yontemi")
  if not mode then return end

  gg.setRanges(DEFAULT_RANGES)
  gg.clearResults()

  if mode == 1 then
    local v = ask("Su anki deger", "")
    if not v or v == "" then return end
    searchIsFuzzy = false
    gg.searchNumber(v, searchType)
  else
    -- Fuzzy arama tum bellegin anlik goruntusunu alir; sonraki adimlarda
    -- degerin hangi yone gittigini soylemek yeterlidir.
    searchIsFuzzy = true
    gg.searchFuzzy("0", gg.SIGN_FUZZY_EQUAL, searchType)
  end

  refineMenu()
end

--=========================================================================
-- 2) Yapi haritasi
--=========================================================================

-- Bir degeri bulduktan sonra komsu alanlar genelde ayni nesnededir: can
-- bulunduysa maksimum can, mana ve seviye birkac bayt otededir. Hepsini tek
-- tek aramak yerine cevreyi dokmek cok daha hizlidir.
local function structMap()
  local res = gg.getResults(30)
  if not res or #res == 0 then
    gg.alert("Once bir arama yapin, sonra bu menuyu kullanin.")
    return
  end
  local labels = {}
  for i, r in ipairs(res) do
    labels[i] = fmtAddr(r.address) .. " = " .. tostring(r.value)
  end
  local pick = gg.choice(labels, nil, "Merkez adresi secin")
  if not pick then return end

  local span = tonumber(ask("Kac bayt cevre taransin?", "128")) or 128
  local center = res[pick].address

  local probes = {}
  for off = -span, span, 4 do
    probes[#probes + 1] = { address = center + off, flags = gg.TYPE_DWORD }
  end
  local dwords = gg.getValues(probes) or {}
  for i = 1, #probes do probes[i].flags = gg.TYPE_FLOAT end
  local floats = gg.getValues(probes) or {}

  local lines = {}
  for i = 1, #probes do
    local off = -span + (i - 1) * 4
    local d = dwords[i] and dwords[i].value or 0
    local f = floats[i] and floats[i].value or 0
    -- Rastgele baytlar float olarak okununca 1e-38 gibi anlamsiz sayilar
    -- uretir; ondalik sutunu yalnizca makul araliklarda gosteriyoruz.
    local fs = (math.abs(f) > 0.001 and math.abs(f) < 1e7)
               and string.format("  f=%.2f", f) or ""
    lines[#lines + 1] = string.format("%+5d  %s%s", off, tostring(d), fs)
  end
  gg.alert("Merkez " .. fmtAddr(center) .. "\noffset  DWORD\n"
           .. table.concat(lines, "\n"))
end

--=========================================================================
-- 3) Kayitli girisler
--=========================================================================

local function showEntries()
  if #entries == 0 then gg.alert("Kayitli giris yok.") return end
  local lines = {}
  for i, e in ipairs(entries) do
    local v, addr = readEntry(e)
    local where = e.absolute and ("mutlak " .. fmtAddr(e.offset))
                  or string.format("%s+0x%X", e.region, e.offset)
    lines[i] = string.format("%s [%s] %s\n   -> %s", e.name,
      TYPE_NAMES[e.typeId] or "?", where,
      v ~= nil and (fmtAddr(addr) .. " = " .. tostring(v))
                or ("COZULEMEDI: " .. tostring(addr)))
  end
  gg.alert(table.concat(lines, "\n\n"))
end

local function editEntry()
  if #entries == 0 then gg.alert("Kayitli giris yok.") return end
  local labels = {}
  for i, e in ipairs(entries) do
    labels[i] = e.name .. (e.freeze and "  [YAZILIYOR]" or "")
      .. (e.below ~= "" and ("  <" .. e.below) or "")
  end
  local pick = gg.choice(labels, nil, "Giris")
  if not pick then return end
  local e = entries[pick]

  local act = gg.choice({
    "Bir kez deger yaz",
    e.freeze and "Farm dongusunden cikar" or "Farm dongusune ekle",
    "Kosul: yalnizca su degerin altindayken yaz",
    "Sil",
  }, nil, e.name)
  if not act then return end

  if act == 1 then
    local v = ask("Yeni deger", e.value or "")
    if not v then return end
    local addr = resolveEntry(e)
    if not addr then gg.alert("Adres cozulemedi.") return end
    gg.setValues({ { address = addr, flags = e.typeId, value = v } })
    e.value = v
    saveEntries()
    gg.toast("yazildi")
  elseif act == 2 then
    e.freeze = not e.freeze
    saveEntries()
    gg.toast(e.freeze and "donguye eklendi" or "donguden cikarildi")
  elseif act == 3 then
    local v = ask("Esik (bos birakirsaniz kosul kalkar)", e.below or "", "text")
    if v == nil then return end
    e.below = v
    saveEntries()
    gg.toast(v ~= "" and ("kosul: < " .. v) or "kosul kaldirildi")
  else
    table.remove(entries, pick)
    saveEntries()
    gg.toast("silindi")
  end
end

--=========================================================================
-- 4) Farm dongusu
--=========================================================================

-- GG'nin kendi dondurma listesinden farki kosul destegi: "can 500'un
-- altina duserse 5000 yaz" gibi bir kural kurulabiliyor. Kosulsuz girisler
-- her turda yazilir.
local function farmLoop()
  local active = {}
  for _, e in ipairs(entries) do
    if e.freeze then active[#active + 1] = e end
  end
  if #active == 0 then
    gg.alert("Donguye eklenmis giris yok.\n\n"
      .. "Once 'Giris duzenle' menusunden bir girisi donguye ekleyin.")
    return
  end

  local period = tonumber(ask("Donguler arasi bekleme (ms)", "500")) or 500
  gg.toast(string.format("%d giris izleniyor. Menu icin GG ikonuna dokunun.",
                         #active))
  gg.setVisible(false)

  local writes = 0
  while true do
    if gg.isVisible() then
      gg.setVisible(false)
      local c = gg.choice({ "Devam et", "Durdur" }, nil,
                          string.format("%d yazma yapildi", writes))
      if c ~= 1 then
        gg.setVisible(true)
        return
      end
    end

    for _, e in ipairs(active) do
      local addr = resolveEntry(e)
      if addr then
        local write = true
        if e.below and e.below ~= "" then
          local got = gg.getValues({ { address = addr, flags = e.typeId } })
          local cur = got and got[1] and tonumber(got[1].value)
          local limit = tonumber(e.below)
          write = (cur ~= nil and limit ~= nil and cur < limit)
        end
        if write then
          gg.setValues({ { address = addr, flags = e.typeId, value = e.value } })
          writes = writes + 1
        end
      end
    end
    gg.sleep(period)
  end
end

--=========================================================================
-- Ana menu
--=========================================================================

local function main()
  entries = loadEntries()
  local info = gg.getTargetInfo()
  local title = string.format("m3gg %s\n%s", VERSION,
    (info and info.packageName) or "hedef secilmedi")

  while true do
    local c = gg.choice({
      "Deger ara",
      "Sonuclardan kaydet",
      "Yapi haritasi (komsu alanlar)",
      "Kayitli girisleri goster",
      "Giris duzenle / donguye ekle / sil",
      "Farm dongusunu baslat",
      "Cikis",
    }, nil, title)

    if not c or c == 7 then
      gg.setVisible(true)
      os.exit()
    elseif c == 1 then searchFlow()
    elseif c == 2 then saveFromResults()
    elseif c == 3 then structMap()
    elseif c == 4 then showEntries()
    elseif c == 5 then editEntry()
    elseif c == 6 then farmLoop()
    end
  end
end

if gg.isVisible() then gg.setVisible(false) end
main()
