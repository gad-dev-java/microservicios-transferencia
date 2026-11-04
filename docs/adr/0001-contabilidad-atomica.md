# ADR 0001: contabilidad atómica para transferencias internas

- Estado: aceptada para el MVP.
- Contexto: ambas cuentas de una transferencia interna pertenecen al mismo servicio contable.
- Decisión: Contabilidad es dueño exclusivo de cuentas, saldos y asientos. El débito y el crédito se confirman en una sola transacción PostgreSQL; se bloquean las cuentas en orden estable. Cada `transferId` tiene un único resultado persistido, incluso si fue rechazado.
- Alternativas consideradas: una base por cuenta y saga de compensación; débito y crédito en llamadas separadas.
- Motivo: la transacción local permite una garantía de integridad más simple y comprobable para este dominio. Una saga queda reservada para una futura integración con un sistema externo.
- Consecuencias: Contabilidad concentra las escrituras financieras; habrá que medir su rendimiento y mantener su API interna idempotente.
- Verificación: pruebas P-01, P-02, P-07 y P-09.

Referencia técnica: [PostgreSQL, bloqueos de filas](https://www.postgresql.org/docs/current/explicit-locking.html).
