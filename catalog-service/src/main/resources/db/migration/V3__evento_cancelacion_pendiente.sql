ALTER TABLE evento
    ADD COLUMN cancelacion_pendiente BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_evento_cancelacion_pendiente
    ON evento (cancelacion_pendiente)
    WHERE cancelacion_pendiente;
