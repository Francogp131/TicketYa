CREATE TABLE IF NOT EXISTS notificacion (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    tipo VARCHAR(60) NOT NULL,
    destinatario_id UUID NOT NULL,
    destinatario_email VARCHAR(255),
    asunto VARCHAR(255) NOT NULL,
    contenido TEXT NOT NULL,
    estado VARCHAR(20) NOT NULL,
    intentos INT NOT NULL DEFAULT 0,
    proximo_reintento TIMESTAMPTZ,
    ultimo_error TEXT,
    correlation_id VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    enviada_at TIMESTAMPTZ,
    CONSTRAINT uk_notificacion_evento_tipo_destinatario UNIQUE (event_id, tipo, destinatario_id),
    CONSTRAINT chk_notificacion_estado CHECK (estado IN ('PENDIENTE', 'ENVIADA', 'FALLIDA'))
);

CREATE INDEX IF NOT EXISTS idx_notificacion_destinatario_created
    ON notificacion (destinatario_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notificacion_estado_reintento
    ON notificacion (estado, proximo_reintento);
