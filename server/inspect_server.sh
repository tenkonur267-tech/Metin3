#!/usr/bin/env bash
# HardMobile sunucu kesif betigi.
# Sunucunun sahibi olarak kendi makinende calistir. Hicbir sey degistirmez,
# sadece "elimde ne var" sorusunu cevaplar. Ciktiyi Claude'a yapistir.
#
# Kullanim:  bash inspect_server.sh 2>&1 | tee server_report.txt

echo "=================== HARDMOBILE SUNUCU KESFI ==================="
echo "tarih: $(date)"; echo "host: $(hostname 2>/dev/null)"; echo "os: $(uname -a 2>/dev/null)"
echo

echo "### 1. Calisan sunucu surecleri ###"
ps aux 2>/dev/null | grep -Ei '(game|db|auth|node|java|mysql|maria)' | grep -v grep | awk '{print $11, $12, $13}'
echo

echo "### 2. Dinlenen portlar ###"
(ss -tlnp 2>/dev/null || netstat -tlnp 2>/dev/null) | grep LISTEN | awk '{print $4}' | sort -u
echo

echo "### 3. Metin2 tipik dosya izleri ###"
for f in game db auth CONFIG conf.txt mob_proto item_proto player.txt; do
  find / -maxdepth 6 -iname "*$f*" 2>/dev/null | grep -vE '/proc/|/sys/' | head -3
done | sort -u | head -30
echo

echo "### 4. Calistirilabilir sunucu ikili/scriptleri ###"
find / -maxdepth 6 \( -name 'game' -o -name 'db' -o -name '*.jar' -o -name 'server.js' -o -name 'app.js' \) -type f 2>/dev/null | grep -vE '/proc|/sys|node_modules' | head -20
echo

echo "### 5. Veritabani ###"
command -v mysql >/dev/null && echo "mysql client: VAR ($(mysql --version 2>/dev/null))" || echo "mysql client: yok"
command -v mariadb >/dev/null && echo "mariadb: VAR"
echo "-- calisan DB motoru --"
ps aux 2>/dev/null | grep -Ei 'mysqld|mariadbd' | grep -v grep | awk '{print $11}' | head -1
echo

echo "### 6. Dil/derleme ipuclari ###"
command -v g++ >/dev/null && echo "g++: VAR"
command -v node >/dev/null && echo "node: $(node -v 2>/dev/null)"
command -v java >/dev/null && echo "java: $(java -version 2>&1 | head -1)"
find / -maxdepth 5 -name 'Makefile' 2>/dev/null | grep -viE 'node_modules|/usr/' | head -5
echo
echo "=================== KESIF BITTI ==================="
echo "Bu ciktinin tamamini Claude'a yapistir; hicbir sifre/token icermez."
