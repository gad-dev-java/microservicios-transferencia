# ADR 0002: outbox antes de publicar eventos en Kafka

- Estado: aceptada para la etapa de eventos.
- Contexto: tras confirmar asientos, Notificaciones e Historial deben conocer el hecho sin bloquear la transferencia.
- Decisión: Contabilidad inserta el evento en `outbox` dentro de la misma transacción de asientos. Un relay lo publica en Kafka. Cada consumidor deduplica por `eventId`.
- Alternativas consideradas: publicar directamente a Kafka dentro del método de negocio; transacción distribuida entre PostgreSQL y Kafka.
- Motivo: evita perder un evento por una caída entre el commit de PostgreSQL y la publicación. El relay puede repetir publicaciones, por eso se exige idempotencia de consumidores.
- Consecuencias: existe retraso eventual, se supervisan eventos pendientes y se necesita un proceso relay. El primer relay puede ser una tarea programada; Debezium se evalúa solo si aporta una mejora demostrable.
- Verificación: P-11 y P-12.

Referencia técnica: [Debezium Outbox Event Router](https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html).
