-- Clave de idempotencia de las devoluciones internas (tarea 3.6).
-- Reintentar el mismo Idempotency-Key no repone la zona dos veces.

CREATE TABLE devolucion_aplicada (
    clave      TEXT PRIMARY KEY,
    evento_id  UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
