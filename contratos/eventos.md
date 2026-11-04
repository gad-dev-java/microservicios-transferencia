# Contratos internos y evento inicial

Los endpoints internos no se exponen mediante el gateway. Se autentican con una identidad de servicio y aceptan identificadores estables para reintentos.

## Evaluación de Riesgo

`POST /internal/v1/evaluaciones` recibe `transferId`, `sourceAccountId`, `destinationAccountId`, `amount` como cadena decimal y `currency`. Devuelve `APROBADA` o `RECHAZADA`, más `reasonCode` y `ruleVersion`. `transferId` es único: una repetición devuelve la decisión persistida. El servicio no mueve dinero.

## Contabilidad

`GET /internal/v1/cuentas/{accountId}` permite a Transferencias validar la cuenta de origen antes de consultar a Riesgo. Devuelve `ownerSubject`, moneda y estado; una cuenta inexistente responde `404`. Solo se acepta una identidad de servicio autorizada. La API pública de cuentas nunca devuelve `ownerSubject`.

`POST /internal/v1/movimientos/transferencia` recibe `transferId`, `actorSubject`, cuentas, importe y moneda. Devuelve `CONFIRMADA` o `RECHAZADA` con `reasonCode`. Una repetición del mismo `transferId` devuelve el resultado persistido. Un cuerpo diferente para ese `transferId` genera conflicto.

`GET /internal/v1/movimientos/transferencia/{transferId}` permite resolver un timeout del comando. Consulta el resultado persistido en `comandos`, incluido un rechazo. Un `404` indica que no se encontró resultado; Transferencias puede reintentar con el mismo `transferId`. La consulta y el reintento se ejecutan fuera de una transacción abierta en Transferencias.

## Evento `TransferenciaConfirmada`, versión 1

Se introduce junto con Kafka y outbox. El evento se escribe en outbox en la misma transacción que los asientos. La clave de partición Kafka es `transferId`. Consumidores aplican deduplicación por `eventId`.

Contrato verificable: [transferencia-confirmada-v1.schema.json](transferencia-confirmada-v1.schema.json).

```json
{
  "eventId": "ae8bfb17-a418-4a14-8745-c83ae943e83e",
  "eventType": "TransferenciaConfirmada",
  "schemaVersion": 1,
  "transferId": "7e689af7-a905-4c9d-b604-c7f6796aa17d",
  "actorSubject": "11111111-1111-4111-8111-111111111111",
  "sourceAccountId": "57bb817f-bc91-44b9-a381-b74f39881d39",
  "destinationAccountId": "2214f54b-0472-4154-8ee4-273df07b9512",
  "amount": "100.00",
  "currency": "PEN",
  "occurredAt": "2026-11-03T15:30:00Z"
}
```

El evento no contiene saldos, nombres, direcciones ni datos de contacto. `eventId`, `eventType`, `schemaVersion`, `transferId`, `amount`, `currency` y `occurredAt` son obligatorios. El consumidor ignora versiones que no entiende y las envía a una cola de revisión; no las interpreta como versión 1. Cambios incompatibles incrementan `schemaVersion` y requieren plan de migración de consumidores.

El evento representa un hecho contable confirmado. No se usa como comando para efectuar el débito. Su publicación puede repetirse, por lo que la unicidad se asegura en cada consumidor.
