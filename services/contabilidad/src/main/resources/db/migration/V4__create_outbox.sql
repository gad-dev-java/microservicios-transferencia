CREATE TABLE outbox (
    event_id UUID PRIMARY KEY,
    transfer_id UUID NOT NULL UNIQUE REFERENCES comandos(transfer_id),
    event_type VARCHAR(64) NOT NULL CHECK (event_type = 'TransferenciaConfirmada'),
    schema_version INTEGER NOT NULL CHECK (schema_version = 1),
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_outbox_pending ON outbox (next_attempt_at, created_at) WHERE published_at IS NULL;
