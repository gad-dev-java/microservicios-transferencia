# HU-09. Historial de transferencias confirmadas

Historial consume `TransferenciaConfirmada` v1 desde Kafka con el grupo `historial-v1`. Guarda en MongoDB una vista de lectura con `_id = eventId`; también exige `transferenciaId` único y comprueba el hash cuando llega el mismo `eventId` otra vez. Confirma el offset de Kafka solo después de insertar o reconocer el duplicado. El índice por titular y fecha soporta la búsqueda.

`GET /api/v1/historial?desde=...&hasta=...&cuentaId=...&limite=20` usa el `sub` del JWT para filtrar. Devuelve únicamente transferencias **iniciadas** por ese titular que hayan sido confirmadas. `desde` y `hasta` usan ISO 8601; `limite` permite entre 1 y 100. El servicio no consulta tablas de otros servicios ni calcula saldos. El listado puede atrasarse frente a Contabilidad; la fuente de verdad del dinero sigue siendo PostgreSQL.

## Pruebas

```powershell
.\mvnw.cmd -pl services/historial -am verify
docker compose up -d --build
pwsh -NoProfile -File infra/scripts/verify-history-lag.ps1
```

La prueba Testcontainers comprueba deduplicación, conflicto de contenido, filtros por titular, fecha y cuenta, respuesta sin hash interno y `401` sin token. En Compose, el consumidor nuevo leyó desde el inicio del tópico: offset 2437, atraso 0 y 2436 documentos, por un evento duplicado previo. La prueba de atraso detuvo Historial, confirmó una transferencia con débito de 0,01 PEN, constató que la proyección seguía vacía para ese `transferId`, reinició Historial y encontró una entrada sin otro cambio de saldo.

MongoDB persiste en `mongo_data` en Compose. La proyección solo puede reconstruirse desde Kafka mientras los eventos sigan retenidos; para una recuperación fuera de esa ventana se necesitaría un mecanismo de reproyección desde la fuente contable, diseñado y autorizado por separado. No se usa MongoDB como respaldo del libro contable.
