-- Esquema inicial de catalog-service (Entrega 1).
-- Dueño: P1 (catalog). No modificar esta migración una vez mergeada: crear V2__*.sql.

CREATE TABLE lugar (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre      VARCHAR(150) NOT NULL,
    direccion   VARCHAR(255) NOT NULL,
    ciudad      VARCHAR(120) NOT NULL,
    latitud     NUMERIC(9, 6) NOT NULL CHECK (latitud BETWEEN -90 AND 90),
    longitud    NUMERIC(9, 6) NOT NULL CHECK (longitud BETWEEN -180 AND 180),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE sector (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lugar_id          UUID NOT NULL REFERENCES lugar (id) ON DELETE CASCADE,
    nombre            VARCHAR(120) NOT NULL,
    tipo              VARCHAR(20) NOT NULL CHECK (tipo IN ('NUMERADO', 'NO_NUMERADO')),
    capacidad         INTEGER NOT NULL CHECK (capacidad > 0),
    filas             INTEGER,
    asientos_por_fila INTEGER,
    UNIQUE (lugar_id, nombre),
    CONSTRAINT sector_numerado_chk CHECK (
        (tipo = 'NUMERADO'
            AND filas IS NOT NULL AND filas > 0
            AND asientos_por_fila IS NOT NULL AND asientos_por_fila > 0)
        OR (tipo = 'NO_NUMERADO'
            AND filas IS NULL
            AND asientos_por_fila IS NULL)
    )
);

CREATE TABLE asiento (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sector_id UUID NOT NULL REFERENCES sector (id) ON DELETE CASCADE,
    fila      INTEGER NOT NULL CHECK (fila > 0),
    numero    INTEGER NOT NULL CHECK (numero > 0),
    UNIQUE (sector_id, fila, numero)
);

CREATE TABLE evento (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre            VARCHAR(200) NOT NULL,
    descripcion       TEXT,
    tipo              VARCHAR(40) NOT NULL,
    organizador_id    UUID NOT NULL,
    lugar_id          UUID NOT NULL REFERENCES lugar (id),
    fecha             TIMESTAMPTZ NOT NULL,
    estado            VARCHAR(20) NOT NULL DEFAULT 'BORRADOR'
                      CHECK (estado IN ('BORRADOR', 'PUBLICADO', 'AGOTADO', 'CANCELADO', 'FINALIZADO')),
    limite_por_cliente INTEGER NOT NULL DEFAULT 4 CHECK (limite_por_cliente > 0),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_evento_busqueda ON evento (estado, fecha, tipo, organizador_id, lugar_id);

CREATE TABLE evento_participante (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    evento_id UUID NOT NULL REFERENCES evento (id) ON DELETE CASCADE,
    nombre    VARCHAR(150) NOT NULL
);

CREATE INDEX idx_evento_participante ON evento_participante (evento_id, nombre);

CREATE TABLE evento_sector_precio (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    evento_id UUID NOT NULL REFERENCES evento (id) ON DELETE CASCADE,
    sector_id UUID NOT NULL REFERENCES sector (id),
    precio    NUMERIC(12, 2) NOT NULL CHECK (precio >= 0),
    UNIQUE (evento_id, sector_id)
);
