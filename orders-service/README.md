# orders-service

Servicio de órdenes, pagos, entradas/QR, devoluciones y cancelación de TicketYa.

- **Puerto:** 8083
- **Base:** `orders_db` (PostgreSQL 16, puerto 5435)
- **Responsable:** P3 — ver [`docs/equipo/compa-3.md`](../docs/equipo/compa-3.md)
- **Contrato:** [`docs/api-contract.md`](../docs/api-contract.md), sección 5.

## Endpoints implementados (Día 1)

| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| POST | `/api/orders` | autenticado | Crea una orden `PENDIENTE_PAGO` desde una reserva vigente |
| GET | `/api/orders/{ordenId}` | dueño | Detalle de la orden |

El pago, la emisión de entradas/QR, la idempotencia completa, las compensaciones y las devoluciones corresponden a las tareas 4.3–4.11 (Días 2–4).

## Creación de la orden

1. Valida `Idempotency-Key` (8–100 caracteres).
2. Consulta la reserva a inventory (`GET /internal/reservations/{id}`).
3. Valida dueño (`clienteId == X-User-Id`) y vigencia; reserva vencida → `410 RESERVATION_EXPIRED`.
4. Guarda el snapshot de evento, sector, asiento y precio.
5. Calcula el total en el servidor sumando los subtotales y crea la orden `PENDIENTE_PAGO`.

## Modelo

`orden`, `orden_item`, `pago`, `entrada`, `devolucion` e `idempotency_key`. Flyway en [`src/main/resources/db/migration`](src/main/resources/db/migration).

## Variables de entorno

| Variable | Default | Uso |
|---|---|---|
| `SERVER_PORT` | `8083` | Puerto HTTP |
| `DB_URL` | `jdbc:postgresql://localhost:5435/orders_db` | Conexión JDBC |
| `DB_USER` / `DB_PASSWORD` | `ticketya` | Credenciales |
| `INVENTORY_URL` | `http://localhost:8082` | Cliente interno hacia inventory |

## Ejecutar y probar

```bash
docker compose -f docker-compose.dev.yml up -d orders-db
cd orders-service
mvn test
mvn spring-boot:run
curl http://localhost:8083/actuator/health
```

Swagger UI: <http://localhost:8083/swagger-ui.html> · OpenAPI: <http://localhost:8083/v3/api-docs>
