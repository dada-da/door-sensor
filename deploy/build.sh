#!/usr/bin/env bash
#
# Build toan bo he thong thanh MOT file jar, roi day sang Raspberry Pi.
#
# Chay tren LAPTOP, khong chay tren Pi. Build Java tren Pi rat cham va de het
# RAM; build o day roi copy sang thi nhanh hon nhieu lan.
#
# Cach dung:
#   ./deploy/build.sh                      # chi build, khong day di
#   ./deploy/build.sh pi@192.168.1.50      # build roi day sang Pi
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET="${1:-}"

echo "==> 1/3 Build dashboard"
cd "$ROOT/dashboard"
npm install
npm run build

echo "==> 2/3 Nhung dashboard vao backend"
# Spring Boot tu phuc vu moi thu trong resources/static o duong dan goc.
# Nho vay dashboard va API chung mot origin: khong can CORS, khong can proxy,
# va chi co mot tien trinh phai quan ly tren Pi.
STATIC="$ROOT/backend/src/main/resources/static"
rm -rf "$STATIC"
mkdir -p "$STATIC"
cp -r "$ROOT/dashboard/dist/." "$STATIC/"

echo "==> 3/3 Build backend"
cd "$ROOT/backend"
mvn -q clean package -DskipTests

JAR=$(ls target/door-sensor-*.jar | grep -v original | head -1)
SIZE=$(du -h "$JAR" | cut -f1)
echo ""
echo "Xong: $JAR ($SIZE)"

if [ -z "$TARGET" ]; then
    echo ""
    echo "Chay thu tai cho:  java -jar $JAR"
    echo "Day sang Pi:       ./deploy/build.sh pi@192.168.1.50"
    exit 0
fi

echo ""
echo "==> Day sang $TARGET"
ssh "$TARGET" "mkdir -p ~/door-sensor"
scp "$JAR" "$TARGET:~/door-sensor/door-sensor.jar"

echo ""
echo "Da copy. Tren Pi chay:"
echo "  sudo systemctl restart door-sensor"
echo "hoac neu chua cai dich vu:"
echo "  java -jar ~/door-sensor/door-sensor.jar"
