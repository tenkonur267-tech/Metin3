-- m3gg.lua'nin saf mantigini sahte bir gg ile dogrular: offset aritmetigi,
-- kalici kayit bicimi, ve bolge adi olmayan adreslerin mutlak saklanmasi.
--
-- Calistirma:  lua5.4 tests/test_gg.lua

local TMP = os.getenv("TMPDIR") or "/tmp"
local SANDBOX = TMP .. "/m3gg_test"
os.execute('rm -rf "' .. SANDBOX .. '"')

local failures = 0
local function check(cond, msg)
  if cond then
    print("  ok   " .. msg)
  else
    failures = failures + 1
    print("  FAIL " .. msg)
  end
end

-- Betigin kullandigi kutuphane bolgesi: taban 0x1000, deger 0x1040'ta,
-- yani beklenen offset 0x40.
local LIB_BASE = 0x70000000
local HP_ADDR = LIB_BASE + 0x40
local ANON_ADDR = 0x12340000

local function makeGG(script)
  local step = 0
  local gg = {
    TYPE_DWORD = 4, TYPE_FLOAT = 16, TYPE_QWORD = 32,
    TYPE_DOUBLE = 64, TYPE_WORD = 2, TYPE_BYTE = 1,
    REGION_C_ALLOC = 1, REGION_ANONYMOUS = 2, REGION_C_BSS = 4,
    REGION_C_DATA = 8, REGION_C_HEAP = 16,
    SIGN_FUZZY_EQUAL = 0, SIGN_FUZZY_NOT_EQUAL = 1,
    SIGN_FUZZY_GREATER = 2, SIGN_FUZZY_LESS = 3,
    EXT_STORAGE = SANDBOX,
    alerts = {}, toasts = {},
  }
  function gg.isVisible() return false end
  function gg.setVisible() end
  function gg.setRanges() end
  function gg.clearResults() end
  function gg.searchNumber() end
  function gg.refineNumber() end
  function gg.searchFuzzy() end
  function gg.refineFuzzy() end
  function gg.getResultsCount() return #(gg.results or {}) end
  function gg.getResults() return gg.results or {} end
  function gg.getTargetInfo() return { packageName = "com.hardmobile.client" } end
  function gg.sleep() end
  function gg.toast(m) gg.toasts[#gg.toasts + 1] = m end
  function gg.alert(m) gg.alerts[#gg.alerts + 1] = m end
  function gg.getValues(t)
    local out = {}
    for i, v in ipairs(t) do
      out[i] = { address = v.address, flags = v.flags,
                 value = gg.memory[v.address] or 0 }
    end
    return out
  end
  function gg.setValues(t)
    for _, v in ipairs(t) do gg.memory[v.address] = tonumber(v.value) end
  end
  function gg.getRangesList(filter)
    local all = {
      { start = LIB_BASE, ["end"] = LIB_BASE + 0x1000, name = "libgame.so" },
      { start = LIB_BASE + 0x1000, ["end"] = LIB_BASE + 0x2000, name = "libgame.so" },
      { start = ANON_ADDR, ["end"] = ANON_ADDR + 0x1000, name = "" },
    }
    if not filter or filter == "" then return all end
    local out = {}
    for _, r in ipairs(all) do
      if r.name == filter then out[#out + 1] = r end
    end
    return out
  end
  function gg.choice()
    step = step + 1
    return script.choices[step]
  end
  function gg.prompt()
    local p = table.remove(script.prompts, 1)
    return p and { p } or nil
  end
  gg.memory = { [HP_ADDR] = 1500, [ANON_ADDR] = 42 }
  return gg
end

local function runScript(gg)
  _G.gg = gg
  local realExit = os.exit
  os.exit = function() error("__EXIT__", 0) end
  local chunk = assert(loadfile("gg/m3gg.lua"))
  local ok, err = pcall(chunk)
  os.exit = realExit
  if not ok and err ~= "__EXIT__" then error(err, 0) end
end

--=========================================================================
print("named region entry")
--=========================================================================
-- Menu 2 (sonuclardan kaydet) -> adresi sec (1) -> isim gir -> menu 7 (cikis)
local gg = makeGG({ choices = { 2, 1, 7 }, prompts = { "hp" } })
gg.results = { { address = HP_ADDR, value = 1500, flags = gg.TYPE_DWORD } }
runScript(gg)

local stored = assert(loadfile(SANDBOX .. "/m3gg/offsets.lua"))()
check(#stored == 1, "bir giris kaydedildi")
check(stored[1].name == "hp", "isim dogru")
check(stored[1].region == "libgame.so", "bolge adi kaydedildi")
check(stored[1].offset == 0x40,
      string.format("offset taban farki olarak hesaplandi (0x%X)", stored[1].offset))
check(stored[1].absolute == false, "adlandirilmis bolge mutlak degil")

--=========================================================================
print("anonymous region entry")
--=========================================================================
os.execute('rm -rf "' .. SANDBOX .. '"')
gg = makeGG({ choices = { 2, 1, 7 }, prompts = { "gold" } })
gg.results = { { address = ANON_ADDR, value = 42, flags = gg.TYPE_DWORD } }
runScript(gg)

stored = assert(loadfile(SANDBOX .. "/m3gg/offsets.lua"))()
check(stored[1].absolute == true, "anonim adres mutlak olarak isaretlendi")
check(stored[1].offset == ANON_ADDR, "mutlak adres oldugu gibi saklandi")
check(#gg.alerts > 0 and gg.alerts[1]:find("anonim"),
      "kullanici anonim bolge konusunda uyarildi")

--=========================================================================
print("entry survives a region that moved")
--=========================================================================
-- Kayitli giris offset olarak tutuldugu icin, kutuphane baska bir adrese
-- yuklendiginde de dogru adresi vermelidir.
os.execute('rm -rf "' .. SANDBOX .. '"')
gg = makeGG({ choices = { 2, 1, 7 }, prompts = { "hp" } })
gg.results = { { address = HP_ADDR, value = 1500, flags = gg.TYPE_DWORD } }
runScript(gg)

local MOVED = 0x7A000000
local gg2 = makeGG({ choices = { 4, 7 }, prompts = {} })
gg2.getRangesList = function(filter)
  local all = { { start = MOVED, ["end"] = MOVED + 0x2000, name = "libgame.so" } }
  if not filter or filter == "" then return all end
  local out = {}
  for _, r in ipairs(all) do
    if r.name == filter then out[#out + 1] = r end
  end
  return out
end
gg2.memory = { [MOVED + 0x40] = 1234 }
runScript(gg2)
check(#gg2.alerts > 0 and gg2.alerts[1]:find("1234") ~= nil,
      "tasinmis kutuphanede deger yeniden cozuldu")

print("")
if failures == 0 then
  print("TUM TESTLER GECTI")
else
  print(failures .. " TEST BASARISIZ")
  os.exit(1)
end
