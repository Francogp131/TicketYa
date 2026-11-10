-- Reintegros parciales por entrada y seguimiento de reintegros (FIX-CARO P0).
-- Se documenta el uso de la tabla reintegro; pago.estado se amplía con REINTEGRO_PENDIENTE
-- para seguir el ciclo de vida de las solicitudes de reintegro.

ALTER TABLE pago DROP CONSTRAINT pago_estado_check;
ALTER TABLE pago ADD CONSTRAINT pago_estado_check
    CHECK (estado IN ('PENDIENTE', 'APROBADO', 'RECHAZADO', 'REINTEGRADO', 'REINTEGRO_PENDIENTE'));

CREATE TABLE reintegro (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pago_id     UUID NOT NULL REFERENCES pago (id),
    entrada_id  UUID REFERENCES entrada (id),
    monto       NUMERIC(12, 2) NOT NULL CHECK (monto >= 0),
    estado      VARCHAR(20) NOT NULL
                CHECK (estado IN ('PENDIENTE', 'APROBADO')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_reintegro_pago_entrada UNIQUE (pago_id, entrada_id)
);

CREATE INDEX idx_reintegro_pago ON reintegro (pago_id);
CREATE INDEX idx_reintegro_estado ON reintegro (estado);
