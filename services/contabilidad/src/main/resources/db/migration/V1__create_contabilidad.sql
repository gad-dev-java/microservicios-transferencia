CREATE TABLE cuentas (
    id uuid PRIMARY KEY,
    owner_subject varchar(128) NOT NULL,
    currency char(3) NOT NULL CHECK (currency = 'PEN'),
    status varchar(16) NOT NULL CHECK (status IN ('ACTIVA', 'INACTIVA')),
    opening_balance numeric(19, 2) NOT NULL CHECK (opening_balance >= 0),
    balance numeric(19, 2) NOT NULL CHECK (balance >= 0),
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_cuentas_owner ON cuentas (owner_subject);

CREATE TABLE movimientos (
    id uuid PRIMARY KEY,
    transfer_id uuid NOT NULL UNIQUE,
    actor_subject varchar(128) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE comandos (
    transfer_id uuid PRIMARY KEY,
    request_hash char(64) NOT NULL,
    outcome varchar(16) NOT NULL CHECK (outcome IN ('PROCESSING', 'CONFIRMADA', 'RECHAZADA')),
    reason_code varchar(64),
    movement_id uuid UNIQUE REFERENCES movimientos (id),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_comando_resultado CHECK (
        (outcome = 'PROCESSING' AND reason_code IS NULL AND movement_id IS NULL)
        OR (outcome = 'CONFIRMADA' AND reason_code IS NULL AND movement_id IS NOT NULL)
        OR (outcome = 'RECHAZADA' AND reason_code IS NOT NULL AND movement_id IS NULL)
    )
);

CREATE TABLE asientos (
    id uuid PRIMARY KEY,
    movement_id uuid NOT NULL REFERENCES movimientos (id),
    account_id uuid NOT NULL REFERENCES cuentas (id),
    delta numeric(19, 2) NOT NULL CHECK (delta <> 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (movement_id, account_id)
);

CREATE INDEX idx_asientos_cuenta_fecha ON asientos (account_id, created_at DESC);
