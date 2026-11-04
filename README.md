# Simulador de transferencias bancarias

Proyecto de portafolio para demostrar backend Java con microservicios en un dominio bancario simulado. HU-01 implementa el núcleo de Contabilidad; la API pública y la autenticación llegan en H2.

- [Arquitectura y conexiones](ARQUITECTURA.md)
- [Diagrama editable en draw.io](ARQUITECTURA.drawio)
- [Entregables y orden de implementación](docs/README.md)
- [Historias de usuario y backlog](docs/07-backlog-de-implementacion.md)
- [Ramas y pull requests por historia](docs/08-flujo-git.md)
- [Contrato OpenAPI público](contratos/openapi-publica.yaml)

El MVP es una transferencia interna en PEN entre cuentas de prueba, con autenticación, evaluación de riesgo, asiento contable atómico e idempotencia. Kafka, Notificaciones y observabilidad completan la siguiente entrega; Redis, MongoDB, Config Server y Kubernetes son ampliaciones posteriores.

## Estado actual: HU-01 (H1)

Contabilidad registra una transferencia interna en una transacción PostgreSQL. Guarda el resultado por `transferId`, bloquea ambas cuentas en orden estable y crea exactamente dos asientos opuestos cuando confirma. Los rechazos por saldo o estado se persisten sin mover fondos.

### Requisitos locales

- JDK 21 en `JAVA_HOME`.
- Docker Desktop activo para PostgreSQL y las pruebas con Testcontainers.
- Maven Wrapper incluido en el repositorio; no hace falta instalar Maven por separado.

### Comandos

```powershell
docker compose up -d postgres
.\mvnw.cmd clean verify
java -jar services/contabilidad/target/contabilidad-0.1.0-SNAPSHOT.jar --spring.profiles.active=local
```

PostgreSQL queda disponible en `localhost:5432`, con base inicial `contabilidad`, usuario `banco_app` y contraseña local de demostración `local-dev-only`. Las bases `transferencias`, `riesgo` y `notificaciones` se crean al inicializar un volumen nuevo. Puedes cambiar puerto y contraseña con las variables de [.env.example](.env.example). No uses esos valores de demostración en un entorno compartido.

El perfil `local` ejecuta la migración Flyway y carga las tres [cuentas ficticias](infra/fixtures/README.md). La API interna escucha en `127.0.0.1:8082`. No hay autenticación en esta fase; no se debe exponer el puerto fuera del equipo. H2 añadirá identidad de servicio antes de conectar el gateway.

En otra terminal, prueba una transferencia de 100.00 PEN:

```powershell
$transferId = [guid]::NewGuid().ToString()
$body = @{
  transferId = $transferId
  actorSubject = '11111111-1111-4111-8111-111111111111'
  sourceAccountId = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1'
  destinationAccountId = 'cccccccc-cccc-4ccc-8ccc-ccccccccccc1'
  amount = '100.00'
  currency = 'PEN'
} | ConvertTo-Json
Invoke-RestMethod http://127.0.0.1:8082/internal/v1/movimientos/transferencia -Method Post -ContentType application/json -Body $body
Invoke-RestMethod "http://127.0.0.1:8082/internal/v1/movimientos/transferencia/$transferId"
Invoke-RestMethod http://127.0.0.1:8082/internal/v1/cuentas/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1
```

Repetir el mismo `transferId` y cuerpo devuelve el mismo resultado. Reutilizarlo con otro importe devuelve `409`. Para reiniciar los saldos de demostración en un volumen local nuevo, usa `docker compose down -v` **solo si deseas borrar todos los datos locales de PostgreSQL**.
