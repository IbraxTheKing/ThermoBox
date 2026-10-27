# CPU and memory of the ThermoBox containers (REST server AND MySQL) during a load test, every few
# seconds, into a CSV file to open in a spreadsheet. Run it in a second window, next to k6; Ctrl+C stops it.
#
#   powershell -ExecutionPolicy Bypass -File loadtest\docker-stats.ps1 [-Interval 5] [-Out docker-stats.csv]
param([int]$Interval = 5, [string]$Out = "docker-stats.csv")

"time,container,cpu_percent,memory_used,memory_limit,memory_percent" | Set-Content -Encoding utf8 $Out
Write-Host "Writing $Out every $Interval s (Ctrl+C to stop)"
while ($true) {
    $time = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    docker stats --no-stream --format "{{.Name}};{{.CPUPerc}};{{.MemUsage}};{{.MemPerc}}" |
        Where-Object { $_ -match "thermobox" } |
        ForEach-Object {
            $name, $cpu, $mem, $memPct = $_ -split ";"
            $used, $limit = $mem -split " / "
            "$time,$name,$($cpu.TrimEnd('%')),$used,$limit,$($memPct.TrimEnd('%'))"
        } | Add-Content -Encoding utf8 $Out
    Start-Sleep -Seconds $Interval
}
