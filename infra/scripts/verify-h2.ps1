param(
    [string]$Api = 'http://localhost:8080',
    [string]$Identity = 'http://localhost:8180'
)

$ErrorActionPreference = 'Stop'
$http = [System.Net.Http.HttpClient]::new()
$http.Timeout = [TimeSpan]::FromSeconds(20)
$source = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1'
$smallSource = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1'
$destination = 'cccccccc-cccc-4ccc-8ccc-ccccccccccc1'
$inactive = 'dddddddd-dddd-4ddd-8ddd-ddddddddddd1'

function Assert-That([bool]$condition, [string]$message) {
    if (-not $condition) { throw $message }
}

function Get-Token([string]$username, [string]$password) {
    (Invoke-RestMethod -Method Post -Uri "$Identity/realms/banco-demo/protocol/openid-connect/token" `
        -Body @{grant_type='password';client_id='banco-cli';username=$username;password=$password}).access_token
}

function New-TransferBody([string]$from, [string]$to, [string]$amount) {
    @{cuentaOrigenId=$from;cuentaDestinoId=$to;importe=$amount;moneda='PEN'} | ConvertTo-Json -Compress
}

function New-Request([string]$method, [string]$path, [string]$token, [string]$body, [string]$key) {
    $request = [System.Net.Http.HttpRequestMessage]::new($method, "$Api$path")
    $request.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $token)
    if ($key) { [void]$request.Headers.TryAddWithoutValidation('Idempotency-Key', $key) }
    if ($body) { $request.Content = [System.Net.Http.StringContent]::new($body, [Text.Encoding]::UTF8, 'application/json') }
    $request
}

function Read-Result($response) {
    $raw = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    $payload = if ($raw) { $raw | ConvertFrom-Json } else { $null }
    [pscustomobject]@{ Status=[int]$response.StatusCode; Body=$payload }
}

function Send-Request([string]$method, [string]$path, [string]$token, [string]$body='', [string]$key='') {
    $request = New-Request $method $path $token $body $key
    try { Read-Result ($http.SendAsync($request).GetAwaiter().GetResult()) }
    finally { $request.Dispose() }
}

try {
    $user1 = Get-Token 'cliente1' 'cliente1-local'
    $user2 = Get-Token 'cliente2' 'cliente2-local'
    $before = Send-Request 'GET' "/api/v1/cuentas/$source" $user1
    Assert-That ($before.Status -eq 200) 'No se puede consultar la cuenta de origen'

    $valid = New-TransferBody $source $destination '1.00'
    $p01 = Send-Request 'POST' '/api/v1/transferencias' $user1 $valid ([guid]::NewGuid().ToString())
    $after = Send-Request 'GET' "/api/v1/cuentas/$source" $user1
    Assert-That ($p01.Status -eq 201 -and $p01.Body.estado -eq 'CONFIRMADA') 'P-01 falló: transferencia válida'
    Assert-That (([decimal]$before.Body.saldoDisponible - [decimal]$after.Body.saldoDisponible) -eq 1) 'P-01 falló: saldo de origen'
    Write-Output 'P-01 OK: transferencia confirmada y saldo actualizado'

    $smallBefore = Send-Request 'GET' "/api/v1/cuentas/$smallSource" $user1
    $p02 = Send-Request 'POST' '/api/v1/transferencias' $user1 (New-TransferBody $smallSource $destination '2999.00') ([guid]::NewGuid().ToString())
    Assert-That ($p02.Status -eq 201 -and $p02.Body.estado -eq 'RECHAZADA' -and $p02.Body.motivo -eq 'INSUFFICIENT_FUNDS') 'P-02 falló'
    $smallAfter = Send-Request 'GET' "/api/v1/cuentas/$smallSource" $user1
    Assert-That ([decimal]$smallAfter.Body.saldoDisponible -eq [decimal]$smallBefore.Body.saldoDisponible) 'P-02 falló: cambió el saldo'
    Write-Output 'P-02 OK: saldo insuficiente'

    $p03 = Send-Request 'POST' '/api/v1/transferencias' $user1 (New-TransferBody $destination $source '1.00') ([guid]::NewGuid().ToString())
    Assert-That ($p03.Status -eq 404) 'P-03 falló: cuenta ajena'
    Write-Output 'P-03 OK: cuenta de origen ajena oculta'

    $p04 = Send-Request 'POST' '/api/v1/transferencias' $user1 (New-TransferBody $source $inactive '1.00') ([guid]::NewGuid().ToString())
    Assert-That ($p04.Status -eq 201 -and $p04.Body.estado -eq 'RECHAZADA' -and $p04.Body.motivo -eq 'ACCOUNT_INACTIVE') 'P-04 falló'
    $afterRejected = Send-Request 'GET' "/api/v1/cuentas/$source" $user1
    Assert-That ([decimal]$afterRejected.Body.saldoDisponible -eq [decimal]$after.Body.saldoDisponible) 'P-04 falló: cambió el saldo'
    Write-Output 'P-04 OK: destino inactivo'

    $parallelKey = [guid]::NewGuid().ToString()
    $parallelBody = New-TransferBody $source $destination '0.01'
    $requests = @(1..10 | ForEach-Object { New-Request 'POST' '/api/v1/transferencias' $user1 $parallelBody $parallelKey })
    try {
        $tasks = @($requests | ForEach-Object { $http.SendAsync($_) })
        $results = @($tasks | ForEach-Object { Read-Result ($_.GetAwaiter().GetResult()) })
    } finally { $requests | ForEach-Object { $_.Dispose() } }
    $ids = @($results | ForEach-Object { $_.Body.id } | Select-Object -Unique)
    Assert-That ($ids.Count -eq 1 -and @($results | Where-Object { $_.Status -notin 200,201 }).Count -eq 0) 'P-05 falló'
    $afterParallel = Send-Request 'GET' "/api/v1/cuentas/$source" $user1
    Assert-That (([decimal]$afterRejected.Body.saldoDisponible - [decimal]$afterParallel.Body.saldoDisponible) -eq [decimal]0.01) 'P-05 falló: débito duplicado'
    Write-Output 'P-05 OK: diez solicitudes paralelas, un identificador'

    $p06 = Send-Request 'POST' '/api/v1/transferencias' $user1 (New-TransferBody $source $destination '0.02') $parallelKey
    Assert-That ($p06.Status -eq 409) 'P-06 falló'
    Write-Output 'P-06 OK: misma clave y cuerpo distinto'

    $current = Send-Request 'GET' "/api/v1/cuentas/$source" $user1
    $balance = [decimal]$current.Body.saldoDisponible
    $raceAmount = [decimal]::Min(3000, [decimal]::Round($balance * 0.7, 2))
    Assert-That ($raceAmount -gt 0 -and $raceAmount -le 3000 -and ($raceAmount * 2) -gt $balance) 'P-07 requiere saldo de prueba suficiente; usa un volumen local nuevo'
    $raceBody = New-TransferBody $source $destination ($raceAmount.ToString('0.00', [Globalization.CultureInfo]::InvariantCulture))
    $raceRequests = @(
        (New-Request 'POST' '/api/v1/transferencias' $user1 $raceBody ([guid]::NewGuid().ToString())),
        (New-Request 'POST' '/api/v1/transferencias' $user1 $raceBody ([guid]::NewGuid().ToString()))
    )
    try {
        $raceTasks = @($raceRequests | ForEach-Object { $http.SendAsync($_) })
        $race = @($raceTasks | ForEach-Object { Read-Result ($_.GetAwaiter().GetResult()) })
    } finally { $raceRequests | ForEach-Object { $_.Dispose() } }
    Assert-That (@($race | Where-Object { $_.Body.estado -eq 'CONFIRMADA' }).Count -eq 1) 'P-07 falló: confirmaciones'
    Assert-That (@($race | Where-Object { $_.Body.motivo -eq 'INSUFFICIENT_FUNDS' }).Count -eq 1) 'P-07 falló: rechazo'
    $afterRace = Send-Request 'GET' "/api/v1/cuentas/$source" $user1
    Assert-That (($balance - [decimal]$afterRace.Body.saldoDisponible) -eq $raceAmount) 'P-07 falló: saldo después de concurrencia'
    Write-Output 'P-07 OK: dos operaciones concurrentes, solo una confirmada'

    $other = Send-Request 'GET' "/api/v1/transferencias/$($p01.Body.id)" $user2
    Assert-That ($other.Status -eq 404) 'P-10 falló'
    Write-Output 'P-10 OK: transferencia ajena oculta'

    docker compose stop riesgo *> $null
    try {
        $beforeOutage = Send-Request 'GET' "/api/v1/cuentas/$source" $user1
        $p08 = Send-Request 'POST' '/api/v1/transferencias' $user1 (New-TransferBody $source $destination '0.01') ([guid]::NewGuid().ToString())
        Assert-That ($p08.Status -eq 201 -and $p08.Body.estado -eq 'RECHAZADA' -and $p08.Body.motivo -eq 'RIESGO_NO_DISPONIBLE') 'P-08 falló'
        $afterOutage = Send-Request 'GET' "/api/v1/cuentas/$source" $user1
        Assert-That ([decimal]$afterOutage.Body.saldoDisponible -eq [decimal]$beforeOutage.Body.saldoDisponible) 'P-08 falló: se movieron fondos'
        Write-Output 'P-08 OK: Riesgo caído, sin movimiento contable'
    } finally { docker compose start riesgo *> $null }

    Write-Output 'P-09: verificado por TransferApiIntegrationTest (respuesta perdida y recuperación)'
    Write-Output 'H2 OK'
} finally {
    $http.Dispose()
}
