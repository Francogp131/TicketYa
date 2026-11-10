-- Estados de pago parcial y unicidad del reintegro de compensación.

ALTER TABLE pago DROP CONSTRAINT pago_estado_check;
ALTER TABLE pago ADD CONSTRAINT pago_estado_check
    CHECK (estado IN ('PENDIENTE', 'APROBADO', 'RECHAZADO', 'REINTEGRADO',
                      'REINTEGRO_PENDIENTE', 'PARCIALMENTE_REINTEGRADO'));

CREATE UNIQUE INDEX uq_reintegro_compensacion
    ON reintegro (pago_id)
    WHERE entrada_id IS NULL;
