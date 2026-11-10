-- Estado intermedio para una entrada cuya devolución ya empezó y todavía no terminó.
-- Evita que una entrada reintegrada siga apareciendo como VALIDA mientras inventory
-- todavía no liberó el lugar.

ALTER TABLE entrada DROP CONSTRAINT entrada_estado_check;
ALTER TABLE entrada ADD CONSTRAINT entrada_estado_check
    CHECK (estado IN ('VALIDA', 'UTILIZADA', 'DEVUELTA', 'EN_REINTEGRO', 'EN_DEVOLUCION'));
