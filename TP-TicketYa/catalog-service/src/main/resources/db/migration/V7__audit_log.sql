-- Bitácora de operaciones críticas de catálogo (9.2)
CREATE TABLE audit_log (
    id             UUID PRIMARY KEY,
    evento_id      UUID NOT NULL,
    accion         VARCHAR(40) NOT NULL,
    usuario_id     UUID NOT NULL,
    usuario_rol    VARCHAR(20) NOT NULL,
    resultado      VARCHAR(20) NOT NULL,
    detalle        TEXT,
    correlation_id VARCHAR(120),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_audit_resultado CHECK (resultado IN ('EXITOSO', 'RECHAZADO'))
);

CREATE INDEX idx_audit_log_evento_created ON audit_log (evento_id, created_at DESC);
CREATE INDEX idx_audit_log_correlation ON audit_log (correlation_id);
