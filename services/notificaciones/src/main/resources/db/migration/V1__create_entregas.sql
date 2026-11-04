CREATE TABLE entregas (
    event_id UUID PRIMARY KEY,
    transfer_id UUID NOT NULL,
    owner_subject VARCHAR(128) NOT NULL,
    event_hash CHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL CHECK (status = 'SIMULADA'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_entregas_owner ON entregas(owner_subject, created_at DESC, event_id DESC);
