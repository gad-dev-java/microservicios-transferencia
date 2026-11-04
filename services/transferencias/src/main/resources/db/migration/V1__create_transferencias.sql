CREATE TABLE transferencias (
    id UUID PRIMARY KEY,
    owner_subject VARCHAR(100) NOT NULL,
    idempotency_key UUID NOT NULL,
    request_hash CHAR(64) NOT NULL,
    source_account_id UUID NOT NULL,
    destination_account_id UUID NOT NULL,
    amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL CHECK (currency = 'PEN'),
    state VARCHAR(32) NOT NULL CHECK (state IN ('RECIBIDA','EVALUANDO','CONTABILIZANDO','PENDIENTE_CONCILIACION','CONFIRMADA','RECHAZADA')),
    reason_code VARCHAR(80),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (owner_subject, idempotency_key)
);
CREATE INDEX idx_transferencias_owner_page ON transferencias (owner_subject, created_at DESC, id DESC);
CREATE INDEX idx_transferencias_recovery ON transferencias (next_attempt_at, created_at)
    WHERE state IN ('CONTABILIZANDO','PENDIENTE_CONCILIACION');
