set -euo pipefail

BROKER="${BROKER:-localhost}"
PORT="${PORT:-1883}"
DEVICE_ID="${1:-door-01}"
LABEL="${2:-Cua chinh}"

STATE_TOPIC="home/door/${DEVICE_ID}/state"
STATUS_TOPIC="home/door/${DEVICE_ID}/status"

if ! command -v mosquitto_pub >/dev/null 2>&1; then
    echo "Khong tim thay mosquitto_pub."
    echo "  Ubuntu/Debian : sudo apt install mosquitto-clients"
    echo "  macOS         : brew install mosquitto"
    exit 1
fi

# Pin bat dau day va tut dan, de bieu do pin tren dashboard co gi de ve.
battery_mv=4150
boot_count=1
state="CLOSED"

publish_status() {
    mosquitto_pub -h "$BROKER" -p "$PORT" -t "$STATUS_TOPIC" -m "$1" -q 1 -r
}

cleanup() {
    echo ""
    echo "Dang tat thiet bi gia lap..."
    publish_status "offline"
    exit 0
}
trap cleanup INT TERM

publish_status "online"
echo "Thiet bi gia lap [$DEVICE_ID] da len mang, dang gui toi $BROKER:$PORT"
echo "Topic: $STATE_TOPIC"
echo "Nhan Ctrl-C de dung."
echo ""

while true; do
    # Doi ngau nhien 5-25 giay giua hai lan doi trang thai.
    sleep_for=$(( (RANDOM % 20) + 5 ))
    sleep "$sleep_for"

    if [ "$state" = "CLOSED" ]; then
        state="OPEN"
    else
        state="CLOSED"
    fi

    # RSSI dao quanh -60 dBm, gan dung voi thuc te trong nha.
    rssi=$(( -75 + (RANDOM % 25) ))

    # Moi chu ky thuc day tieu ton mot chut pin.
    battery_mv=$(( battery_mv - (RANDOM % 3) ))
    boot_count=$(( boot_count + 1 ))

    ts=$(date +%s)
    payload=$(printf '{"device_id":"%s","label":"%s","state":"%s","ts":%s,"battery_mv":%s,"rssi":%s,"boot_count":%s}' \
        "$DEVICE_ID" "$LABEL" "$state" "$ts" "$battery_mv" "$rssi" "$boot_count")

    mosquitto_pub -h "$BROKER" -p "$PORT" -t "$STATE_TOPIC" -m "$payload" -q 1

    printf '[%s] %-6s  pin %s mV  rssi %s dBm\n' \
        "$(date '+%H:%M:%S')" "$state" "$battery_mv" "$rssi"
done
