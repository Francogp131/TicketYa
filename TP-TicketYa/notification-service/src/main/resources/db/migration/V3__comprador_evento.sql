CREATE TABLE IF NOT EXISTS comprador_evento (
    id UUID PRIMARY KEY,
    evento_id UUID NOT NULL,
    cliente_id UUID NOT NULL,
    cliente_email VARCHAR(255),
    evento_nombre VARCHAR(255),
    evento_fecha TIMESTAMPTZ,
    evento_ciudad VARCHAR(120),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_comprador_evento_evento_cliente UNIQUE (evento_id, cliente_id)
);

CREATE INDEX IF NOT EXISTS idx_comprador_evento_evento
    ON comprador_evento (evento_id);
