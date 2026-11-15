ALTER TABLE evento
    ADD COLUMN alta_demanda BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE evento
    ADD COLUMN inventario_version BIGINT NOT NULL DEFAULT 0
        CHECK (inventario_version >= 0);

-- Los eventos ya publicados tienen un snapshot en inventory con version 1.
-- Arrancar en 1 evita que la primera edición sea ignorada como versión vieja.
UPDATE evento
SET inventario_version = 1
WHERE estado IN ('PUBLICADO', 'AGOTADO');
