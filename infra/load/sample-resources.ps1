param(
    [string]$Output = 'infra/load/tec18-resources.csv',
    [int]$Samples = 10,
    [int]$IntervalSeconds = 30
)

'timestamp,name,cpu_percent,memory' | Set-Content -LiteralPath $Output
for ($sample = 0; $sample -lt $Samples; $sample++) {
    $stamp = (Get-Date).ToUniversalTime().ToString('o')
    docker stats --no-stream --format '{{.Name}},{{.CPUPerc}},{{.MemUsage}}' |
        ForEach-Object { "$stamp,$_" } |
        Add-Content -LiteralPath $Output
    if ($sample -lt $Samples - 1) { Start-Sleep -Seconds $IntervalSeconds }
}
