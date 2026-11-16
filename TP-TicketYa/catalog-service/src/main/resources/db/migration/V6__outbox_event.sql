-- Outbox transaccional para eventos de dominio de catálogo (3.2)
CREATE TABLE outbox_event (
    id              UUID PRIMARY KEY,
    event_id        UUID NOT NULL UNIQUE,
    type            VARCHAR(80) NOT NULL,
    aggregate_id    UUID NOT NULL,
    occurred_at     TIMESTAMPTZ NOT NULL,
    correlation_id  VARCHAR(120),
    version         INTEGER NOT NULL DEFAULT 1,
    payload         JSONB NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts        INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ,
    last_error      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED'))
);

CREATE INDEX idx_outbox_event_status_next ON outbox_event (status, next_attempt_at);
CREATE INDEX idx_outbox_event_aggregate ON outbox_event (aggregate_id, occurred_at);