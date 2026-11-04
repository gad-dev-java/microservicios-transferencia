# Simulador de transferencias bancarias

MVP local de transferencias ficticias con Java 21, Maven, Spring Boot, PostgreSQL y Keycloak. Gateway publica la API; Transferencias coordina Riesgo y Contabilidad.

- [Arquitectura y conexiones](ARQUITECTURA.md)
- [Diagrama editable en draw.io](ARQUITECTURA.drawio)
- [Entregables y orden de implementación](docs/README.md)
- [Historias de usuario y backlog](docs/07-backlog-de-implementacion.md)
- [Ramas y pull requests por historia](docs/08-flujo-git.md)
- [Contrato OpenAPI público](contratos/openapi-publica.yaml)
- [Guía para agentes y asistentes de código](AGENTS.md)
- [CI y registro de imágenes](docs/10-ci-y-registro.md)
- [Colección Postman de demostración](contratos/coleccion-demo.postman_collection.json)
- [Prueba de carga y resultados](docs/11-prueba-carga.md)
- [Límite distribuido con Redis](docs/12-limite-distribuido.md)
- [Historial asíncrono con MongoDB](docs/13-historial.md)
- [Config Server y configuración versionada](docs/14-config-server.md)

El MVP es una transferencia interna en PEN entre cuentas de prueba, con autenticación, evaluación de riesgo, asiento contable atómico e idempotencia. H3 incorpora outbox, Kafka y avisos simulados. H4 añade métricas y trazas. H5 añade Redis para límite distribuido, Historial con MongoDB y Config Server; Kubernetes es la siguiente ampliación.

## Estado actual: H5, configuración centralizada

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

La misma clave y el mismo cuerpo devuelven `200` con el mismo identificador; un cuerpo distinto devuelve `409`. Una respuesta `202` indica que la conciliación continúa y se puede consultar el estado con `GET /api/v1/transferencias/{id}`. `GET /api/v1/notificaciones` muestra avisos simulados del titular autenticado. Solo las confirmaciones escriben `TransferenciaConfirmada` v1 en outbox dentro de la transacción contable; el relay lo publica en Kafka y Notificaciones deduplica por `eventId`.

El script H2 comprueba P-01 a P-08 y P-10 por el gateway. P-09 se prueba con Testcontainers. Para P-11, detén Kafka con `docker compose stop kafka`, confirma una transferencia y verifica una fila de `outbox` con `published_at IS NULL` en la base `contabilidad`; al ejecutar `docker compose start kafka`, el aviso aparece en `GET /api/v1/notificaciones`. Para P-12, la prueba `NotificationIntegrationTest` repite el mismo `eventId` y verifica una sola entrega. También se comprobó reenviando el evento por Kafka en Compose.

### Demostración guiada

1. Ejecuta `docker compose up -d --build` y espera a que `http://localhost:8080/actuator/health/readiness` devuelva `200`.
2. Importa [la colección Postman](contratos/coleccion-demo.postman_collection.json) y ejecútala en el orden numerado. Obtiene los dos tokens, lista cuentas, confirma una transferencia de 0,01 PEN, repite la clave, comprueba un conflicto, provoca un rechazo de Riesgo y verifica que otro titular no ve la cuenta, la transferencia ni el historial.
3. Consulta `GET /api/v1/notificaciones` tras unos segundos para observar el aviso asíncrono. Abre Zipkin y Prometheus para examinar trazas muestreadas y métricas.
4. Ejecuta `pwsh -NoProfile -File infra/scripts/verify-h2.ps1` para comprobar automáticamente los escenarios del MVP. Esta prueba crea transferencias y detiene temporalmente Riesgo; ejecútala fuera de una prueba de carga.

La colección guarda tokens solo en variables de Postman durante la sesión. Las claves y contraseñas incluidas son exclusivamente para el entorno local ficticio. Para una medición sostenida de cinco minutos, usa la [guía de carga](docs/11-prueba-carga.md); la prueba crea unas 1.200 transferencias reales en el volumen local.

También puedes ejecutar la colección sin interfaz gráfica desde Docker:

```powershell
docker run --rm -v "${PWD}/contratos:/etc/newman" postman/newman:alpine run /etc/newman/coleccion-demo.postman_collection.json --env-var gatewayUrl=http://host.docker.internal:8080 --env-var identityUrl=http://host.docker.internal:8180 --bail
```

La colección ampliada en HU-09 pasó las 13 solicitudes y las 13 aserciones; `verify-h2.ps1` también completó P-01 a P-08 y P-10 después de la prueba de carga.

## Observabilidad local

Zipkin está en `http://localhost:9411/zipkin/` y Prometheus en `http://localhost:9090`. Las solicitudes muestreadas aparecen con el mismo `traceId` en Gateway, Transferencias, Riesgo y Contabilidad. Compose muestrea el 10 % por defecto para mantener estable el Zipkin local bajo carga; `TRACING_SAMPLE_PROBABILITY=1.0` permite una demostración breve de trazas completas. Prometheus consulta `/actuator/prometheus` de los cuatro servicios internos; ese endpoint no se enruta por el gateway. Incluye métricas HTTP, decisiones de Riesgo, transferencias por estado, conciliación pendiente, entregas simuladas y atraso de outbox. Las sondas `/actuator/health/liveness` y `/actuator/health/readiness` están habilitadas en Compose.

Las ramas de historias y tareas se apilan en orden. Usa la rama final del hito para reproducir su estado. El Gateway usa Redis para limitar solicitudes por titular y ruta; [la prueba con dos instancias](docs/12-limite-distribuido.md) demuestra una cuota compartida. `GET /api/v1/historial` busca confirmaciones propias en MongoDB con filtros por fecha y cuenta; su [prueba de atraso](docs/13-historial.md) demuestra que el lector no cambia saldos. Riesgo y Transferencias consumen [configuración no sensible versionada](docs/14-config-server.md). Kubernetes sigue en H5.
