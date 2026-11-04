CREATE TABLE evaluaciones (
    transfer_id UUID PRIMARY KEY,
    request_hash CHAR(64) NOT NULL,
    decision VARCHAR(16) NOT NULL CHECK (decision IN ('APROBADA','RECHAZADA')),
    reason_code VARCHAR(80),
    rule_version VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
