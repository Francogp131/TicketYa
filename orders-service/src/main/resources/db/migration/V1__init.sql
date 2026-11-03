-- Esquema inicial de orders-service (Entrega 1).
-- Dueño: P3 (orders). No modificar esta migración una vez mergeada: crear V2__*.sql.

CREATE TABLE orden (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cliente_id    UUID NOT NULL,
    evento_id     UUID NOT NULL,
    evento_nombre VARCHAR(200) NOT NULL,
    evento_fecha  TIMESTAMPTZ NOT NULL,
    reserva_id    UUID NOT NULL,
    estado        VARCHAR(30) NOT NULL
                  CHECK (estado IN ('PENDIENTE_PAGO', 'PAGADA', 'RECHAZADA', 'ERROR_PAGO', 'REINTEGRO_PENDIENTE')),
    medio_pago    VARCHAR(40),
    total         NUMERIC(12, 2) NOT NULL CHECK (total >= 0),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_orden_cliente ON orden (cliente_id, created_at DESC);
CREATE INDEX idx_orden_evento ON orden (evento_id);

CREATE TABLE orden_item (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    orden_id        UUID NOT NULL REFERENCES orden (id) ON DELETE CASCADE,
    sector_id       UUID NOT NULL,
    asiento_id      UUID,
    cantidad        INTEGER NOT NULL CHECK (cantidad > 0),
    precio_unitario NUMERIC(12, 2) NOT NULL CHECK (precio_unitario >= 0),
    subtotal        NUMERIC(12, 2) NOT NULL CHECK (subtotal >= 0)
);

CREATE TABLE pago (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    orden_id    UUID NOT NULL REFERENCES orden (id),
    estado      VARCHAR(20) NOT NULL
                CHECK (estado IN ('PENDIENTE', 'APROBADO', 'RECHAZADO', 'REINTEGRADO')),
    monto       NUMERIC(12, 2) NOT NULL CHECK (monto >= 0),
    medio_pago  VARCHAR(40) NOT NULL,
    token       VARCHAR(40),
    external_id VARCHAR(100),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_pago_orden ON pago (orden_id);

CREATE TABLE entrada (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    orden_id      UUID NOT NULL REFERENCES orden (id),
    orden_item_id UUID NOT NULL REFERENCES orden_item (id),
    cliente_id    UUID NOT NULL,
    evento_id     UUID NOT NULL,
    evento_nombre VARCHAR(200) NOT NULL,
    evento_fecha  TIMESTAMPTZ NOT NULL,
    sector_id     UUID NOT NULL,
    asiento_id    UUID,
    qr_code       UUID NOT NULL UNIQUE,
    estado        VARCHAR(20) NOT NULL DEFAULT 'VALIDA'
                  CHECK (estado IN ('VALIDA', 'UTILIZADA', 'DEVUELTA', 'EN_REINTEGRO')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_entrada_cliente ON entrada (cliente_id, evento_id);
CREATE INDEX idx_entrada_evento ON entrada (evento_id, estado);

CREATE TABLE devolucion (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entrada_id  UUID NOT NULL REFERENCES entrada (id),
    estado      VARCHAR(20) NOT NULL
                CHECK (estado IN ('PENDIENTE', 'PROCESANDO', 'APROBADA', 'RECHAZADA')),
    motivo      VARCHAR(500),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ
);

CREATE INDEX idx_devolucion_estado ON devolucion (estado, created_at);

-- Como máximo una devolución abierta por entrada.
CREATE UNIQUE INDEX uq_devolucion_abierta
    ON devolucion (entrada_id)
    WHERE estado IN ('PENDIENTE', 'PROCESANDO');

CREATE TABLE idempotency_key (
    key             VARCHAR(100) PRIMARY KEY,
    request_hash    VARCHAR(64) NOT NULL,
    response_status INTEGER,
    response_body   JSONB,
    estado          VARCHAR(20) NOT NULL DEFAULT 'EN_PROCESO'
                    CHECK (estado IN ('EN_PROCESO', 'COMPLETADA')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_idempotency_expira ON idempotency_key (expires_at);
