param(
    [string]$Context = 'minikube',
    [string]$Minikube = 'C:\Program Files\Kubernetes\Minikube\minikube.exe',
    [switch]$SkipImageLoad
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$namespace = 'banca-demo'

function Assert-Exit([string]$step) {
    if ($LASTEXITCODE -ne 0) { throw "$step falló (código $LASTEXITCODE)" }
}

function Apply-Generated([string[]]$arguments) {
    $yaml = & kubectl --context $Context -n $namespace @arguments --dry-run=client -o yaml
    Assert-Exit 'Generación del recurso'
    $yaml | & kubectl --context $Context apply -f -
    Assert-Exit 'Aplicación del recurso'
}

if (-not (Test-Path $Minikube)) { throw "Minikube no encontrado: $Minikube" }
& kubectl --context $Context get node minikube -o name | Out-Null
Assert-Exit 'Contexto de Kubernetes'
& kubectl --context $Context apply -f (Join-Path $PSScriptRoot 'namespace.yaml')
Assert-Exit 'Namespace'

$demoEnv = @{}
Get-Content (Join-Path $root '.env.example') | ForEach-Object {
    if ($_ -match '^([A-Z_]+)=(.*)$') { $demoEnv[$Matches[1]] = $Matches[2] }
}
Apply-Generated @('create', 'secret', 'generic', 'demo-secrets',
    "--from-literal=postgres-password=$($demoEnv.POSTGRES_PASSWORD)",
    "--from-literal=keycloak-admin-password=$($demoEnv.KEYCLOAK_ADMIN_PASSWORD)",
    "--from-literal=service-client-secret=$($demoEnv.SERVICE_CLIENT_SECRET)")
Apply-Generated @('create', 'configmap', 'postgres-init',
    "--from-file=01-databases.sql=$(Join-Path $root 'infra/postgres/init/01-databases.sql')")
Apply-Generated @('create', 'configmap', 'keycloak-realm',
    "--from-file=banco-demo-realm.json=$(Join-Path $root 'infra/keycloak/banco-demo-realm.json')")
Apply-Generated @('create', 'configmap', 'config-repo',
    "--from-file=riesgo.yml=$(Join-Path $root 'infra/config-repo/riesgo.yml')",
    "--from-file=transferencias.yml=$(Join-Path $root 'infra/config-repo/transferencias.yml')")
Apply-Generated @('create', 'configmap', 'prometheus-config',
    "--from-file=prometheus.yml=$(Join-Path $root 'infra/prometheus/prometheus.yml')")

if (-not $SkipImageLoad) {
    $images = @(
        'postgres:17-alpine', 'quay.io/keycloak/keycloak:26.8.1',
        'apache/kafka:4.4.0', 'mongo:8.0.32', 'redis:8.2.11-alpine3.22',
        'busybox:1.37', 'openzipkin/zipkin:3.6.1', 'prom/prometheus:v3.15.1'
    ) + @('config-server', 'contabilidad', 'riesgo', 'notificaciones',
        'historial', 'transferencias', 'gateway' | ForEach-Object {
            "proyecto-microservicios-${_}:latest"
        })
    foreach ($image in $images) {
        Write-Output "Cargando $image en Minikube..."
        & $Minikube -p $Context image load $image
        Assert-Exit "Carga de $image"
    }
}

& kubectl --context $Context apply -f (Join-Path $PSScriptRoot 'infra.yaml')
Assert-Exit 'Infraestructura'
& kubectl --context $Context -n $namespace rollout status deployment/postgres deployment/keycloak deployment/kafka deployment/mongo deployment/redis deployment/config-server deployment/zipkin deployment/prometheus --timeout=600s
Assert-Exit 'Arranque de infraestructura'
& kubectl --context $Context apply -f (Join-Path $PSScriptRoot 'apps.yaml')
Assert-Exit 'Aplicaciones'
& kubectl --context $Context -n $namespace rollout status deployment/contabilidad deployment/riesgo deployment/notificaciones deployment/historial deployment/transferencias deployment/gateway --timeout=600s
Assert-Exit 'Arranque de aplicaciones'
& kubectl --context $Context -n $namespace get pods,pvc
Assert-Exit 'Estado final'
