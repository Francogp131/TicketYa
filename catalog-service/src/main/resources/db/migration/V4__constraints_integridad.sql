-- Refuerzos de integridad detectados al validar el ER de catalog contra Flyway.
-- Validan en la base las mismas reglas que ya aplica el dominio y no agregan funcionalidad.

ALTER TABLE evento
    ADD CONSTRAINT evento_tipo_chk
    CHECK (tipo IN ('RECITAL', 'TEATRO', 'DEPORTE', 'CONFERENCIA', 'FESTIVAL', 'OTRO'));

ALTER TABLE sector
    ADD CONSTRAINT sector_capacidad_numerado_chk
    CHECK (tipo <> 'NUMERADO' OR capacidad = filas * asientos_por_fila);
