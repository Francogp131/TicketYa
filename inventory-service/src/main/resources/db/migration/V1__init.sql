-- Esquema inicial de inventory-service (Entrega 1).
-- Dueño: P2 (inventory). No modificar esta migración una vez mergeada: crear V2__*.sql.

CREATE TABLE evento_inventario (
    evento_id          UUID PRIMARY KEY,
    nombre             VARCHAR(200) NOT NULL,
    fecha              TIMESTAMPTZ NOT NULL,
    estado             VARCHAR(20) NOT NULL DEFAULT 'PUBLICADO'
                       CHECK (estado IN ('PUBLICADO', 'AGOTADO', 'CANCELADO', 'FINALIZADO')),
    limite_por_cliente INTEGER NOT NULL CHECK (limite_por_cliente > 0),
    version            BIGINT NOT NULL DEFAULT 1,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE sector_inventario (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    evento_id     UUID NOT NULL REFERENCES evento_inventario (evento_id) ON DELETE CASCADE,
    sector_id     UUID NOT NULL,
    nombre        VARCHAR(120) NOT NULL,
    tipo          VARCHAR(20) NOT NULL CHECK (tipo IN ('NUMERADO', 'NO_NUMERADO')),
    precio        NUMERIC(12, 2) NOT NULL CHECK (precio >= 0),
    capacidad     INTEGER NOT NULL CHECK (capacidad > 0),
    disponibles   INTEGER CHECK (disponibles >= 0),
    UNIQUE (evento_id, sector_id),
    CONSTRAINT sector_inventario_disponibles_chk CHECK (
        (tipo = 'NO_NUMERADO' AND disponibles IS NOT NULL AND disponibles <= capacidad)
        OR (tipo = 'NUMERADO' AND disponibles IS NULL)
    )
);

CREATE TABLE asiento_inventario (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    evento_id  UUID NOT NULL REFERENCES evento_inventario (evento_id) ON DELETE CASCADE,
    sector_id  UUID NOT NULL,
    asiento_id UUID NOT NULL,
    fila       INTEGER NOT NULL CHECK (fila > 0),
    numero     INTEGER NOT NULL CHECK (numero > 0),
    estado     VARCHAR(20) NOT NULL DEFAULT 'LIBRE'
               CHECK (estado IN ('LIBRE', 'RESERVADO', 'VENDIDO')),
    reserva_id UUID,
    expires_at TIMESTAMPTZ,
    UNIQUE (evento_id, asiento_id),
    CONSTRAINT asiento_inventario_estado_chk CHECK (
        (estado = 'LIBRE' AND reserva_id IS NULL AND expires_at IS NULL)
        OR (estado = 'RESERVADO' AND reserva_id IS NOT NULL AND expires_at IS NOT NULL)
        OR (estado = 'VENDIDO' AND reserva_id IS NOT NULL)
    )
);

CREATE INDEX idx_asiento_inventario_sector ON asiento_inventario (evento_id, sector_id, estado);

CREATE TABLE reserva (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    evento_id  UUID NOT NULL REFERENCES evento_inventario (evento_id),
    cliente_id UUID NOT NULL,
    estado     VARCHAR(20) NOT NULL DEFAULT 'VIGENTE'
               CHECK (estado IN ('VIGENTE', 'CONFIRMADA', 'LIBERADA', 'VENCIDA')),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE asiento_inventario
    ADD CONSTRAINT asiento_inventario_reserva_fk
    FOREIGN KEY (reserva_id) REFERENCES reserva (id);

CREATE INDEX idx_reserva_vencimiento ON reserva (estado, expires_at);
CREATE INDEX idx_reserva_cliente ON reserva (evento_id, cliente_id, estado);

CREATE TABLE reserva_item (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reserva_id UUID NOT NULL REFERENCES reserva (id) ON DELETE CASCADE,
    sector_id  UUID NOT NULL,
    asiento_id UUID,
    cantidad   INTEGER NOT NULL CHECK (cantidad > 0),
    UNIQUE (reserva_id, sector_id, asiento_id)
);

CREATE TABLE cliente_evento (
    evento_id         UUID NOT NULL REFERENCES evento_inventario (evento_id),
    cliente_id        UUID NOT NULL,
    entradas_vendidas INTEGER NOT NULL DEFAULT 0 CHECK (entradas_vendidas >= 0),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (evento_id, cliente_id)
);
