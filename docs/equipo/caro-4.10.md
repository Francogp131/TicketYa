# Caro — 4.10 ya está implementada (solo verificar)

La tarea **4.10 (consultas propias)** ya quedó implementada y mergeada en `main` por Franco
(PR #13). **No tenés que escribir código.** Tu parte es verificarla y, si querés, dejar tu
comentario/aprobación en el PR.

## Qué se agregó

Tres endpoints, todos filtrando por el cliente autenticado:

- `GET /api/orders?page=0&size=20` → lista paginada de tus órdenes.
- `GET /api/orders/tickets?eventoId={uuid}&page=0&size=20` → lista paginada de tus entradas.
- `GET /api/orders/tickets/{entradaId}` → detalle de una entrada tuya.
- Entrada de otro cliente → `403 NOT_TICKET_OWNER`.
- `page`/`size` inválidos → `400 VALIDATION_ERROR`.

## Cómo verificarlo (5 minutos)

```bash
git switch main
git pull origin main
docker compose -f docker-compose.dev.yml up -d
```

En cuatro terminales:

```bash
cd catalog-service && mvn spring-boot:run
cd inventory-service && mvn spring-boot:run
cd orders-service && mvn spring-boot:run
cd api-gateway && mvn spring-boot:run
```

Después:

```bash
CLIENTE=33333333-3333-4333-8333-333333333333

curl "http://localhost:8080/api/orders?page=0&size=5" \
  -H "X-User-Id: $CLIENTE" -H "X-User-Role: CLIENT"

curl "http://localhost:8080/api/orders/tickets?page=0&size=5" \
  -H "X-User-Id: $CLIENTE" -H "X-User-Role: CLIENT"
```

Qué tenés que ver:

- `200` con `content`, `page`, `size`, `totalElements` y `totalPages`.
- En órdenes, solo las órdenes de `CLIENTE`.
- En entradas, solo las entradas de `CLIENTE`, con `qrCode` y `estado`.

Prueba rápida de aislamiento: pedí una entrada con otro `X-User-Id` (por ejemplo el ADMIN) y
debe responder `403 NOT_TICKET_OWNER`.

## Si querés aportar algo chico

1. Corré los dos `curl` y mandate la salida a Franco (o pegala en el PR).
2. Revisá el PR #13 y dejá tu aprobación/comentario.
3. Opcional: agregá un test de controller para `GET /api/orders` con `@WebMvcTest`; con eso
   sumás una contribución propia sin tocar la lógica.

Cualquier cosa que no compile, mandame el error y lo vemos.
