set -euo pipefail

BROKER="${BROKER:-localhost}"
PORT="${PORT:-1883}"
DEVICE_ID="${1:-door-01}"
LABEL="${2:-Cua chinh}"
STATE_TOPIC="home/door/${DEVICE_ID}/state"

if ! command -v mosquitto_pub >/dev/null 2>&1; then
    echo "Can cai mosquitto-clients truoc."
    exit 1
fi

now=$(date +%s)
battery=4180
boot=1
count=0

echo "Dang nap lich su 24 gio cho [$DEVICE_ID]..."

# Duyet nguoc tu 24 gio truoc den hien tai.
for h in $(seq 24 -1 1); do
    # Mo phong nhip sinh hoat: ban ngay ra vao nhieu, dem gan nhu khong.
    hour_of_day=$(( ($(date -d "@$now" +%-H 2>/dev/null || date -r "$now" +%-H) - h + 24) % 24 ))

    if [ "$hour_of_day" -ge 23 ] || [ "$hour_of_day" -le 5 ]; then
        opens=$(( RANDOM % 2 ))          # dem: 0-1 lan
    elif [ "$hour_of_day" -ge 7 ] && [ "$hour_of_day" -le 9 ]; then
        opens=$(( (RANDOM % 4) + 3 ))    # gio di lam: 3-6 lan
    elif [ "$hour_of_day" -ge 17 ] && [ "$hour_of_day" -le 20 ]; then
        opens=$(( (RANDOM % 4) + 2 ))    # gio ve: 2-5 lan
    else
        opens=$(( RANDOM % 3 ))          # gio hanh chinh: 0-2 lan
    fi

    for _ in $(seq 1 "$opens" 2>/dev/null || true); do
        [ "$opens" -eq 0 ] && break

        offset_in_hour=$(( RANDOM % 3600 ))
        open_ts=$(( now - (h * 3600) + offset_in_hour ))
        # Cua mo trong khoang 5 giay den 3 phut.
        close_ts=$(( open_ts + (RANDOM % 175) + 5 ))
        [ "$close_ts" -gt "$now" ] && continue

        battery=$(( battery - 1 ))
        boot=$(( boot + 1 ))
        rssi=$(( -75 + (RANDOM % 25) ))

        for pair in "OPEN:$open_ts" "CLOSED:$close_ts"; do
            st="${pair%%:*}"
            t="${pair##*:}"
            payload=$(printf '{"device_id":"%s","label":"%s","state":"%s","ts":%s,"battery_mv":%s,"rssi":%s,"boot_count":%s}' \
                "$DEVICE_ID" "$LABEL" "$st" "$t" "$battery" "$rssi" "$boot")
            mosquitto_pub -h "$BROKER" -p "$PORT" -t "$STATE_TOPIC" -m "$payload" -q 1
            count=$(( count + 1 ))
            # Nhip nho de backend kip ghi, va de thu tu su kien khong bi dao.
            sleep 0.05
        done
    done
done

# Ket thuc o trang thai dong, giong mot thiet bi binh thuong.
mosquitto_pub -h "$BROKER" -p "$PORT" -t "home/door/${DEVICE_ID}/status" -m "online" -q 1 -r

echo "Xong. Da gui $count su kien."
echo "Mo http://localhost:5173 de xem."
