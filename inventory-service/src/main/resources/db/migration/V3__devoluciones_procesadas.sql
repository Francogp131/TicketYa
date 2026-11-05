-- Tarea 3.6: registrar devoluciones ya aplicadas para que el endpoint interno
-- POST /internal/returns sea idempotente incluso en zonas no numeradas.
CREATE TABLE devolucion_procesada (
    referencia  UUID PRIMARY KEY,
    evento_id   UUID NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
