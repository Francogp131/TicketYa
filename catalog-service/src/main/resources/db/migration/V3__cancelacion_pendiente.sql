-- Tarea 2.3: recordar cancelaciones que no pudieron avisarse a orders-service.
ALTER TABLE evento
    ADD COLUMN cancelacion_pendiente BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_evento_cancelacion_pendiente
    ON evento (estado, cancelacion_pendiente);
