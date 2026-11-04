-- Datos de prueba de catalog-service (Entrega 1, tarea 5.1).
-- UUIDs fijos y asientos determinísticos para que inventory pueda sembrar el mismo snapshot.
-- Organizador de prueba: 22222222-2222-4222-8222-222222222222
-- Otro organizador:      55555555-5555-4555-8555-555555555555

INSERT INTO lugar (id, nombre, direccion, ciudad, latitud, longitud) VALUES
    ('a0000000-0000-4000-8000-000000000001', 'Estadio Municipal de Tandil', 'Av. Marconi 1000', 'Tandil', -37.321700, -59.133200),
    ('a0000000-0000-4000-8000-000000000002', 'Complejo Cultural Universitario', 'Yrigoyen 3100', 'Tandil', -37.327000, -59.140000);

INSERT INTO sector (id, lugar_id, nombre, tipo, capacidad, filas, asientos_por_fila) VALUES
    ('b0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000001', 'Platea', 'NUMERADO', 20, 4, 5),
    ('b0000000-0000-4000-8000-000000000002', 'a0000000-0000-4000-8000-000000000001', 'Campo', 'NO_NUMERADO', 100, NULL, NULL),
    ('b0000000-0000-4000-8000-000000000003', 'a0000000-0000-4000-8000-000000000002', 'Pullman', 'NUMERADO', 24, 3, 8),
    ('b0000000-0000-4000-8000-000000000004', 'a0000000-0000-4000-8000-000000000002', 'Platea Alta', 'NUMERADO', 12, 2, 6);

-- Asientos determinísticos: c0000000-0000-4000-8000-[sector][fila][numero]000000
INSERT INTO asiento (id, sector_id, fila, numero)
SELECT format('c0000000-0000-4000-8000-%s%s%s000000',
              lpad('1', 2, '0'), lpad(fila::text, 2, '0'), lpad(numero::text, 2, '0'))::uuid,
       'b0000000-0000-4000-8000-000000000001', fila, numero
FROM generate_series(1, 4) AS fila
CROSS JOIN generate_series(1, 5) AS numero;

INSERT INTO asiento (id, sector_id, fila, numero)
SELECT format('c0000000-0000-4000-8000-%s%s%s000000',
              lpad('3', 2, '0'), lpad(fila::text, 2, '0'), lpad(numero::text, 2, '0'))::uuid,
       'b0000000-0000-4000-8000-000000000003', fila, numero
FROM generate_series(1, 3) AS fila
CROSS JOIN generate_series(1, 8) AS numero;

INSERT INTO asiento (id, sector_id, fila, numero)
SELECT format('c0000000-0000-4000-8000-%s%s%s000000',
              lpad('4', 2, '0'), lpad(fila::text, 2, '0'), lpad(numero::text, 2, '0'))::uuid,
       'b0000000-0000-4000-8000-000000000004', fila, numero
FROM generate_series(1, 2) AS fila
CROSS JOIN generate_series(1, 6) AS numero;

INSERT INTO evento (id, nombre, descripcion, tipo, organizador_id, lugar_id, fecha, estado, limite_por_cliente) VALUES
    ('d0000000-0000-4000-8000-000000000001', 'Festival de Rock Tandil 2027',
     'Tres escenarios y bandas locales en el Estadio Municipal.',
     'FESTIVAL', '22222222-2222-4222-8222-222222222222', 'a0000000-0000-4000-8000-000000000001',
     '2027-03-14T21:00:00-03:00', 'PUBLICADO', 4),
    ('d0000000-0000-4000-8000-000000000002', 'El Avaro — Teatro Universitario',
     'Puesta en escena de la obra de Molière.',
     'TEATRO', '22222222-2222-4222-8222-222222222222', 'a0000000-0000-4000-8000-000000000002',
     '2027-04-18T20:30:00-03:00', 'PUBLICADO', 4),
    ('d0000000-0000-4000-8000-000000000003', 'Arquitectura Web 2027 — Charla abierta',
     'Charla sobre microservicios y contratos REST.',
     'CONFERENCIA', '55555555-5555-4555-8555-555555555555', 'a0000000-0000-4000-8000-000000000002',
     '2027-05-20T18:00:00-03:00', 'PUBLICADO', 2);

INSERT INTO evento_participante (evento_id, nombre) VALUES
    ('d0000000-0000-4000-8000-000000000001', 'Los Piojos'),
    ('d0000000-0000-4000-8000-000000000001', 'Callejeros'),
    ('d0000000-0000-4000-8000-000000000002', 'Compañía Teatral Tandil'),
    ('d0000000-0000-4000-8000-000000000003', 'Dra. Ana Pérez');

INSERT INTO evento_sector_precio (evento_id, sector_id, precio) VALUES
    ('d0000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000001', 25000.00),
    ('d0000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000002', 18000.00),
    ('d0000000-0000-4000-8000-000000000002', 'b0000000-0000-4000-8000-000000000003', 12000.00),
    ('d0000000-0000-4000-8000-000000000002', 'b0000000-0000-4000-8000-000000000004', 8000.00),
    ('d0000000-0000-4000-8000-000000000003', 'b0000000-0000-4000-8000-000000000003', 15000.00),
    ('d0000000-0000-4000-8000-000000000003', 'b0000000-0000-4000-8000-000000000004', 10000.00);
