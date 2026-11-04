$ErrorActionPreference = 'Stop'
$gateway = 'http://localhost:8080'
$source = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1'
$destination = 'cccccccc-cccc-4ccc-8ccc-ccccccccccc1'
$token = (Invoke-RestMethod -Method Post -Uri 'http://localhost:8180/realms/banco-demo/protocol/openid-connect/token' `
    -Body @{grant_type='password';client_id='banco-cli';username='cliente1';password='cliente1-local'}).access_token
$headers = @{Authorization="Bearer $token"}

function Get-Balance {
    [decimal](Invoke-RestMethod -Uri "$gateway/api/v1/cuentas/$source" -Headers $headers).saldoDisponible
}

function Get-HistoryCount([string]$transferId) {
    $result = docker compose exec -T mongo mongosh --quiet historial `
        --eval "db.transferencias_confirmadas.countDocuments({transferenciaId: '$transferId'})"
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo consultar MongoDB' }
    [int]$result
}

$before = Get-Balance
docker compose stop historial | Out-Null
try {
    $body = @{cuentaOrigenId=$source;cuentaDestinoId=$destination;importe='0.01';moneda='PEN'} | ConvertTo-Json -Compress
    $created = Invoke-RestMethod -Method Post -Uri "$gateway/api/v1/transferencias" `
        -Headers @{Authorization="Bearer $token";'Idempotency-Key'=[guid]::NewGuid().ToString()} `
        -ContentType 'application/json' -Body $body
    if ($created.estado -ne 'CONFIRMADA') { throw "Transferencia no confirmada: $($created.estado)" }
    $after = Get-Balance
    if (($before - $after) -ne [decimal]0.01) { throw 'El débito contable no coincide' }
    Start-Sleep -Seconds 3
    if ((Get-HistoryCount $created.id) -ne 0) { throw 'La proyección avanzó con Historial detenido' }
    Write-Output 'Historial detenido: transferencia confirmada y saldo actualizado; proyección aún ausente'
} finally {
    docker compose start historial | Out-Null
}

$projected = $false
for ($attempt = 0; $attempt -lt 30; $attempt++) {
    if ((Get-HistoryCount $created.id) -eq 1) { $projected = $true; break }
    Start-Sleep -Seconds 1
}
if (-not $projected) { throw 'Historial no recuperó el evento' }
if ((Get-Balance) -ne $after) { throw 'El consumo del historial cambió el saldo' }
Write-Output "HU-09 OK: transferId=$($created.id), una entrada tras recuperar Historial, saldo estable $after PEN"
