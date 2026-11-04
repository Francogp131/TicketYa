-- Datos de prueba de inventory-service (Entrega 1, tarea 5.1).
-- Replica el snapshot de los eventos publicados del seed de catalog-service.
-- Asientos determinísticos: c0000000-0000-4000-8000-[sector][fila][numero]000000

INSERT INTO evento_inventario (evento_id, nombre, fecha, estado, limite_por_cliente, version) VALUES
    ('d0000000-0000-4000-8000-000000000001', 'Festival de Rock Tandil 2027',
     '2027-03-14T21:00:00-03:00', 'PUBLICADO', 4, 1),
    ('d0000000-0000-4000-8000-000000000002', 'El Avaro — Teatro Universitario',
     '2027-04-18T20:30:00-03:00', 'PUBLICADO', 4, 1),
    ('d0000000-0000-4000-8000-000000000003', 'Arquitectura Web 2027 — Charla abierta',
     '2027-05-20T18:00:00-03:00', 'PUBLICADO', 2, 1);

INSERT INTO sector_inventario (evento_id, sector_id, nombre, tipo, precio, capacidad, disponibles) VALUES
    ('d0000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000001', 'Platea', 'NUMERADO', 25000.00, 20, NULL),
    ('d0000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000002', 'Campo', 'NO_NUMERADO', 18000.00, 100, 100),
    ('d0000000-0000-4000-8000-000000000002', 'b0000000-0000-4000-8000-000000000003', 'Pullman', 'NUMERADO', 12000.00, 24, NULL),
    ('d0000000-0000-4000-8000-000000000002', 'b0000000-0000-4000-8000-000000000004', 'Platea Alta', 'NUMERADO', 8000.00, 12, NULL),
    ('d0000000-0000-4000-8000-000000000003', 'b0000000-0000-4000-8000-000000000003', 'Pullman', 'NUMERADO', 15000.00, 24, NULL),
    ('d0000000-0000-4000-8000-000000000003', 'b0000000-0000-4000-8000-000000000004', 'Platea Alta', 'NUMERADO', 10000.00, 12, NULL);

-- Asientos del evento 1, sector "Platea" (4 x 5)
INSERT INTO asiento_inventario (evento_id, sector_id, asiento_id, fila, numero, estado)
SELECT 'd0000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000001',
       format('c0000000-0000-4000-8000-%s%s%s000000',
              lpad('1', 2, '0'), lpad(fila::text, 2, '0'), lpad(numero::text, 2, '0'))::uuid,
       fila, numero, 'LIBRE'
FROM generate_series(1, 4) AS fila
CROSS JOIN generate_series(1, 5) AS numero;

-- Asientos del evento 2, sector "Pullman" (3 x 8)
INSERT INTO asiento_inventario (evento_id, sector_id, asiento_id, fila, numero, estado)
SELECT 'd0000000-0000-4000-8000-000000000002', 'b0000000-0000-4000-8000-000000000003',
       format('c0000000-0000-4000-8000-%s%s%s000000',
              lpad('3', 2, '0'), lpad(fila::text, 2, '0'), lpad(numero::text, 2, '0'))::uuid,
       fila, numero, 'LIBRE'
FROM generate_series(1, 3) AS fila
CROSS JOIN generate_series(1, 8) AS numero;

-- Asientos del evento 2, sector "Platea Alta" (2 x 6)
INSERT INTO asiento_inventario (evento_id, sector_id, asiento_id, fila, numero, estado)
SELECT 'd0000000-0000-4000-8000-000000000002', 'b0000000-0000-4000-8000-000000000004',
       format('c0000000-0000-4000-8000-%s%s%s000000',
              lpad('4', 2, '0'), lpad(fila::text, 2, '0'), lpad(numero::text, 2, '0'))::uuid,
       fila, numero, 'LIBRE'
FROM generate_series(1, 2) AS fila
CROSS JOIN generate_series(1, 6) AS numero;

-- Asientos del evento 3, sector "Pullman" (3 x 8)
INSERT INTO asiento_inventario (evento_id, sector_id, asiento_id, fila, numero, estado)
SELECT 'd0000000-0000-4000-8000-000000000003', 'b0000000-0000-4000-8000-000000000003',
       format('c0000000-0000-4000-8000-%s%s%s000000',
              lpad('3', 2, '0'), lpad(fila::text, 2, '0'), lpad(numero::text, 2, '0'))::uuid,
       fila, numero, 'LIBRE'
FROM generate_series(1, 3) AS fila
CROSS JOIN generate_series(1, 8) AS numero;

-- Asientos del evento 3, sector "Platea Alta" (2 x 6)
INSERT INTO asiento_inventario (evento_id, sector_id, asiento_id, fila, numero, estado)
SELECT 'd0000000-0000-4000-8000-000000000003', 'b0000000-0000-4000-8000-000000000004',
       format('c0000000-0000-4000-8000-%s%s%s000000',
              lpad('4', 2, '0'), lpad(fila::text, 2, '0'), lpad(numero::text, 2, '0'))::uuid,
       fila, numero, 'LIBRE'
FROM generate_series(1, 2) AS fila
CROSS JOIN generate_series(1, 6) AS numero;
