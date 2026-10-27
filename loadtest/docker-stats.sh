#!/bin/sh
# CPU and memory of the ThermoBox containers (REST server AND MySQL) during a load test, every few
# seconds, into a CSV file to open in a spreadsheet. Run it in a second terminal, next to k6; Ctrl+C stops it.
#
#   sh loadtest/docker-stats.sh [interval in seconds, 5] [file, docker-stats.csv]
INTERVAL="${1:-5}"
OUT="${2:-docker-stats.csv}"

echo "time,container,cpu_percent,memory_used,memory_limit,memory_percent" > "$OUT"
echo "Writing $OUT every $INTERVAL s (Ctrl+C to stop)"
while true; do
    time="$(date '+%Y-%m-%d %H:%M:%S')"
    docker stats --no-stream --format '{{.Name}};{{.CPUPerc}};{{.MemUsage}};{{.MemPerc}}' |
        grep thermobox |
        awk -F';' -v t="$time" '{ split($3, m, " / "); gsub("%", "", $2); gsub("%", "", $4); print t "," $1 "," $2 "," m[1] "," m[2] "," $4 }' >> "$OUT"
    sleep "$INTERVAL"
done
