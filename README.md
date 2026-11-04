# Simulador de transferencias bancarias

MVP local de transferencias ficticias con Java 21, Maven, Spring Boot, PostgreSQL y Keycloak. Gateway publica la API; Transferencias coordina Riesgo y Contabilidad.

- [Arquitectura y conexiones](ARQUITECTURA.md)
- [Diagrama editable en draw.io](ARQUITECTURA.drawio)
- [Entregables y orden de implementación](docs/README.md)
- [Historias de usuario y backlog](docs/07-backlog-de-implementacion.md)
- [Ramas y pull requests por historia](docs/08-flujo-git.md)
- [Contrato OpenAPI público](contratos/openapi-publica.yaml)
- [Guía para agentes y asistentes de código](AGENTS.md)

El MVP es una transferencia interna en PEN entre cuentas de prueba, con autenticación, evaluación de riesgo, asiento contable atómico e idempotencia. Kafka, Notificaciones y observabilidad completan la siguiente entrega; Redis, MongoDB, Config Server y Kubernetes son ampliaciones posteriores.

## Estado actual: H2, MVP seguro

Contabilidad registra el movimiento con dos asientos atómicos e idempotencia por `transferId`. Riesgo guarda una decisión reproducible; Transferencias persiste el estado público, exige `Idempotency-Key` y concilia resultados inciertos. Los servicios validan JWT y los endpoints internos exigen identidad de servicio.

### Requisitos locales

- Docker Desktop para ejecutar el stack y las pruebas con Testcontainers.
- JDK 21 para ejecutar Maven Wrapper fuera de Docker.

### Comandos

```powershell
docker compose up -d --build
docker compose ps
.\mvnw.cmd clean verify
pwsh -NoProfile -File infra/scripts/verify-h2.ps1
```

Gateway escucha en `http://localhost:8080` y Keycloak en `http://localhost:8180`. PostgreSQL queda ligado a `127.0.0.1:5432` para desarrollo; Contabilidad, Riesgo y Transferencias no publican puertos al host. La primera construcción de imágenes descarga Maven y dependencias. Puedes configurar puertos y credenciales locales mediante [.env.example](.env.example). Estos secretos son solo de demostración.

El perfil `local` carga cuatro [cuentas ficticias](infra/fixtures/README.md), incluida una cuenta destino inactiva. Keycloak importa `banco-demo` al crear su volumen por primera vez. Los usuarios son `cliente1` / `cliente1-local` y `cliente2` / `cliente2-local`. El password grant del cliente `banco-cli` existe solo para la demo local.

Obtén un JWT y solicita una transferencia por el gateway:

```powershell
$token = (Invoke-RestMethod -Method Post -Uri 'http://localhost:8180/realms/banco-demo/protocol/openid-connect/token' -Body @{grant_type='password';client_id='banco-cli';username='cliente1';password='cliente1-local'}).access_token
$headers = @{Authorization="Bearer $token"; 'Idempotency-Key'=[guid]::NewGuid().ToString()}
$body = @{cuentaOrigenId='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1';cuentaDestinoId='cccccccc-cccc-4ccc-8ccc-ccccccccccc1';importe='10.00';moneda='PEN'} | ConvertTo-Json
Invoke-RestMethod 'http://localhost:8080/api/v1/cuentas' -Headers @{Authorization="Bearer $token"}
Invoke-RestMethod 'http://localhost:8080/api/v1/transferencias' -Method Post -Headers $headers -ContentType 'application/json' -Body $body
```

La misma clave y el mismo cuerpo devuelven `200` con el mismo identificador; un cuerpo distinto devuelve `409`. Una respuesta `202` indica que la conciliación continúa y se puede consultar el estado con `GET /api/v1/transferencias/{id}`. El script comprueba P-01 a P-08 y P-10 por el gateway; detiene y vuelve a iniciar Riesgo durante P-08. P-09 se prueba con Testcontainers en `TransferApiIntegrationTest`, simulando la pérdida de la respuesta tras el asiento. El script utiliza saldos de demostración y P-07 requiere saldo suficiente en la cuenta de origen. El pipeline ejecuta `verify` y valida Compose.

Las ramas HU-02 a HU-07 se apilan en orden. La rama `feature/TEC-12-demo-integral-H2` añade Compose completo y esta guía; usa esa rama para reproducir el MVP. Kafka, Redis, MongoDB, Config Server y Kubernetes corresponden a etapas posteriores.
