param(
    [string]$Network = 'proyecto-microservicios_default',
    [string]$Image = 'proyecto-microservicios-gateway:latest'
)

$ErrorActionPreference = 'Stop'
$suffix = [guid]::NewGuid().ToString('N').Substring(0, 12)
$nameA = "gateway-limit-a-$suffix"
$nameB = "gateway-limit-b-$suffix"
$routePrefix = "limittest-$suffix-"
$started = @()
$client = [System.Net.Http.HttpClient]::new()
$client.Timeout = [TimeSpan]::FromSeconds(5)

function Start-Gateway([string]$name, [int]$port) {
    $id = docker run --rm -d --name $name --network $Network -p "127.0.0.1:$($port):8080" `
        -e SERVER_ADDRESS=0.0.0.0 `
        -e OIDC_ISSUER=http://localhost:8180/realms/banco-demo `
        -e OIDC_JWK_SET_URI=http://keycloak:8080/realms/banco-demo/protocol/openid-connect/certs `
        -e TRANSFERENCIAS_URL=http://transferencias:8081 `
        -e NOTIFICACIONES_URL=http://notificaciones:8084 `
        -e REDIS_HOST=redis `
        -e RATE_LIMIT_REPLENISH=1 `
        -e RATE_LIMIT_BURST=120 `
        -e RATE_LIMIT_REQUESTED_TOKENS=60 `
        -e ROUTE_ID_PREFIX=$routePrefix `
        -e MANAGEMENT_TRACING_SAMPLING_PROBABILITY=0.0 `
        $Image
    if ($LASTEXITCODE -ne 0) { throw "No se inició $name" }
    $script:started += $name
    Write-Output "${name}: $id"
}

function Wait-Ready([int]$port) {
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        try {
            $response = $client.GetAsync("http://localhost:$port/actuator/health/readiness").GetAwaiter().GetResult()
            if ([int]$response.StatusCode -eq 200) { $response.Dispose(); return }
            $response.Dispose()
        } catch { }
        Start-Sleep -Seconds 2
    }
    throw "Gateway en puerto $port no inició a tiempo"
}

function Get-Account([int]$port, [string]$token) {
    $request = [System.Net.Http.HttpRequestMessage]::new('GET', "http://localhost:$port/api/v1/cuentas")
    $request.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $token)
    try {
        $response = $client.SendAsync($request).GetAwaiter().GetResult()
        try { return [int]$response.StatusCode }
        finally { $response.Dispose() }
    } finally { $request.Dispose() }
}

try {
    Start-Gateway $nameA 18085
    Start-Gateway $nameB 18086
    Wait-Ready 18085
    Wait-Ready 18086

    $token = (Invoke-RestMethod -Method Post -Uri 'http://localhost:8180/realms/banco-demo/protocol/openid-connect/token' `
        -Body @{grant_type='password';client_id='banco-cli';username='cliente1';password='cliente1-local'}).access_token
    $statuses = @(
        (Get-Account 18085 $token),
        (Get-Account 18086 $token),
        (Get-Account 18085 $token),
        (Get-Account 18086 $token)
    )
    if ($statuses.Count -ne 4 -or $statuses[0] -ne 200 -or $statuses[1] -ne 200 -or
        $statuses[2] -ne 429 -or $statuses[3] -ne 429) {
        throw "Límite no compartido entre instancias: $($statuses -join ', ')"
    }
    Write-Output "TEC-20 OK: Gateway A/B/A/B = $($statuses -join ', '); Redis comparte la cuota por titular y ruta"
} finally {
    $client.Dispose()
    foreach ($name in $started) { docker stop $name | Out-Null }
}
