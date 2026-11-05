# Guía para ejecutar el simulador y usar su API

Esta guía cubre el stack local con Docker Compose y la API pública detrás del Gateway. El proyecto simula transferencias ficticias en PEN; no conecta con bancos ni mueve dinero real. Las rutas y esquemas fuente están en [OpenAPI](../contratos/openapi-publica.yaml).

## Requisitos

- Docker Desktop con Docker Compose.
- Para ejecutar Maven fuera de Docker: JDK 21 y Maven Wrapper incluido (`mvnw.cmd`).
- PowerShell 7 para los scripts de verificación.

## Arranque

Desde la raíz del repositorio, en PowerShell:

```powershell
docker compose up -d --build
docker compose ps
```

Espera a que los servicios estén sanos. Gateway debe responder en `http://localhost:8080/actuator/health/readiness` y Keycloak en `http://localhost:8180/health/ready`. Para verificar compilación y pruebas:

```powershell
.\mvnw.cmd clean verify
```

Para ejecutar la comprobación del flujo H2 (usa datos sintéticos y detiene Riesgo temporalmente):

```powershell
pwsh -NoProfile -File infra/scripts/verify-h2.ps1
```

Para apagar los servicios, conservando volúmenes y datos locales:

```powershell
docker compose stop
```

Para levantar el despliegue de Kubernetes, sigue [la guía de Minikube](15-kubernetes.md). No ejecutes Compose y Minikube simultáneamente en un equipo con recursos limitados.

## URLs y autenticación

| Servicio | URL local | Uso |
| --- | --- | --- |
| API Gateway | `http://localhost:8080` | API pública `/api/v1` |
| Keycloak | `http://localhost:8180` | Emisión de tokens y consola de identidad |
| Zipkin | `http://localhost:9411/zipkin/` | Trazas distribuidas |
| Prometheus | `http://localhost:9090` | Métricas |

Usuarios ficticios: `cliente1` / `cliente1-local` y `cliente2` / `cliente2-local`. Solicita un token OAuth:

```powershell
$tokenResponse = Invoke-RestMethod -Method Post `
  -Uri 'http://localhost:8180/realms/banco-demo/protocol/openid-connect/token' `
  -ContentType 'application/x-www-form-urlencoded' `
  -Body @{ grant_type='password'; client_id='banco-cli'; username='cliente1'; password='cliente1-local' }
$token = $tokenResponse.access_token
$headers = @{ Authorization = "Bearer $token" }
```

En las llamadas protegidas usa `Authorization: Bearer <token>`. No compartas ni registres tokens. En el entorno local las credenciales son exclusivamente de demostración.

## API pública

Base URL: `http://localhost:8080/api/v1`. Los ejemplos de respuesta muestran la forma JSON; UUIDs y marcas de tiempo variarán entre ejecuciones.

### Listar las cuentas propias

`GET /cuentas`

```powershell
Invoke-RestMethod 'http://localhost:8080/api/v1/cuentas' -Headers $headers
```

Respuesta `200 OK`:

```json
[
  { "id": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1", "moneda": "PEN", "saldoDisponible": "4000.00", "estado": "ACTIVA" },
  { "id": "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1", "moneda": "PEN", "saldoDisponible": "2500.00", "estado": "ACTIVA" }
]
```

### Consultar una cuenta propia

`GET /cuentas/{cuentaId}`

```powershell
Invoke-RestMethod 'http://localhost:8080/api/v1/cuentas/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1' -Headers $headers
```

Respuesta `200 OK`:

```json
{ "id": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1", "moneda": "PEN", "saldoDisponible": "4000.00", "estado": "ACTIVA" }
```

Cuenta que no existe o no pertenece al titular: `404 Not Found`.

### Crear una transferencia

`POST /transferencias`

Headers requeridos: `Authorization: Bearer <token>`, `Content-Type: application/json` e `Idempotency-Key: <UUID único para esta operación>`.

Body:

```json
{
  "cuentaOrigenId": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1",
  "cuentaDestinoId": "cccccccc-cccc-4ccc-8ccc-ccccccccccc1",
  "importe": "10.00",
  "moneda": "PEN"
}
```

El importe es una cadena decimal positiva con dos posiciones. La cuenta de origen debe pertenecer al usuario autenticado. Ejecución:

```powershell
$transferHeaders = @{
  Authorization = "Bearer $token"
  'Idempotency-Key' = [guid]::NewGuid().ToString()
}
$body = @{
  cuentaOrigenId = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1'
  cuentaDestinoId = 'cccccccc-cccc-4ccc-8ccc-ccccccccccc1'
  importe = '10.00'
  moneda = 'PEN'
} | ConvertTo-Json
Invoke-RestMethod 'http://localhost:8080/api/v1/transferencias' `
  -Method Post -Headers $transferHeaders -ContentType 'application/json' -Body $body
```

Respuesta típica `201 Created` (se confirma o rechaza con resultado definitivo):

```json
{
  "id": "6d30359e-be9d-4925-929b-c4dd32493360",
  "cuentaOrigenId": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1",
  "cuentaDestinoId": "cccccccc-cccc-4ccc-8ccc-ccccccccccc1",
  "importe": "10.00",
  "moneda": "PEN",
  "estado": "CONFIRMADA",
  "motivo": null,
  "creadaEn": "2026-11-04T20:00:00Z",
  "actualizadaEn": "2026-11-04T20:00:01Z"
}
```

Estados definitivos de ejemplo: `CONFIRMADA` o `RECHAZADA`. Una operación rechazada puede devolver un código en `motivo`, como `INSUFFICIENT_FUNDS` o `ACCOUNT_INACTIVE`. Si la contabilidad todavía está conciliándose, se devuelve `202 Accepted` con estado `PENDIENTE_CONCILIACION`.

La misma clave y el mismo body devuelven `200 OK` con la transferencia existente. Reutilizar la misma clave con otro body devuelve `409 Conflict`. Genera una clave nueva por operación lógica.

### Consultar una transferencia

`GET /transferencias/{transferenciaId}`

```powershell
Invoke-RestMethod "http://localhost:8080/api/v1/transferencias/$($result.id)" -Headers $headers
```

Respuesta `200 OK`: el mismo esquema de transferencia anterior. Una transferencia de otro titular devuelve `404 Not Found`.

### Listar transferencias propias

`GET /transferencias?limit=20&cursor={cursor}`

`limit` acepta valores de 1 a 100 y por defecto es 20. `cursor` es opaco y se toma de `siguienteCursor` de la respuesta anterior; omítelo en la primera página.

Respuesta `200 OK`:

```json
{
  "items": [
    {
      "id": "6d30359e-be9d-4925-929b-c4dd32493360",
      "cuentaOrigenId": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1",
      "cuentaDestinoId": "cccccccc-cccc-4ccc-8ccc-ccccccccccc1",
      "importe": "10.00",
      "moneda": "PEN",
      "estado": "CONFIRMADA",
      "motivo": null,
      "creadaEn": "2026-11-04T20:00:00Z",
      "actualizadaEn": "2026-11-04T20:00:01Z"
    }
  ],
  "siguienteCursor": null
}
```

### Consultar notificaciones

`GET /notificaciones`

Devuelve avisos simulados propios. La entrega es asíncrona; puede tardar unos segundos después de confirmar la transferencia.

```json
[
  {
    "eventId": "180e9828-b7fe-4cfa-b15d-3e0a819e1a71",
    "transferenciaId": "6d30359e-be9d-4925-929b-c4dd32493360",
    "estado": "SIMULADA",
    "creadaEn": "2026-11-04T20:00:02Z"
  }
]
```

### Buscar historial

`GET /historial?desde={date-time}&hasta={date-time}&cuentaId={UUID}&limite=20`

Todos los filtros son opcionales. Las fechas deben ser ISO-8601; `limite` por defecto es 20. El historial es una proyección asíncrona de transferencias confirmadas y puede tener atraso.

```json
[
  {
    "eventId": "180e9828-b7fe-4cfa-b15d-3e0a819e1a71",
    "transferenciaId": "6d30359e-be9d-4925-929b-c4dd32493360",
    "cuentaOrigenId": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1",
    "cuentaDestinoId": "cccccccc-cccc-4ccc-8ccc-ccccccccccc1",
    "importe": "10.00",
    "moneda": "PEN",
    "ocurridaEn": "2026-11-04T20:00:01Z"
  }
]
```

## Errores comunes

Las respuestas de error usan este formato:

```json
{ "codigo": "IDEMPOTENCY_CONFLICT", "mensaje": "La clave ya fue utilizada con otra solicitud", "traceId": "7f52d69b9d49a2ae" }
```

| HTTP | Causa frecuente |
| --- | --- |
| `400 Bad Request` | JSON inválido, moneda/formato no admitido o `limit` fuera del rango |
| `401 Unauthorized` | Token faltante, vencido o inválido |
| `404 Not Found` | Cuenta o transferencia inexistente o ajena al titular |
| `409 Conflict` | Clave de idempotencia repetida con datos diferentes |

Usa `traceId` para correlacionar el error con los logs y trazas. No envíes tokens ni datos sensibles al compartir evidencia.

## Colección Postman

Importa [la colección de demostración](../contratos/coleccion-demo.postman_collection.json), configura `gatewayUrl=http://localhost:8080` e `identityUrl=http://localhost:8180` y ejecútala en orden. También puedes correrla sin instalar Newman localmente:

```powershell
docker run --rm -v "${PWD}/contratos:/etc/newman" postman/newman:alpine run /etc/newman/coleccion-demo.postman_collection.json --env-var gatewayUrl=http://host.docker.internal:8080 --env-var identityUrl=http://host.docker.internal:8180 --bail
```

