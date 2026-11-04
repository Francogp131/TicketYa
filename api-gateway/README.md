# api-gateway

Punto de entrada único de TicketYa. Enruta `/api/catalog/**`, `/api/inventory/**` y `/api/orders/**` a los servicios y propaga la identidad de la Entrega 1.

- **Puerto:** 8080
- **Responsable:** P4 — ver [`docs/equipo/compa-4.md`](../docs/equipo/compa-4.md)

## Rutas

| Prefijo | Destino | Variable |
|---|---|---|
| `/api/catalog/**` | catalog-service | `CATALOG_URL` (default `http://localhost:8081`) |
| `/api/inventory/**` | inventory-service | `INVENTORY_URL` (default `http://localhost:8082`) |
| `/api/orders/**` | orders-service | `ORDERS_URL` (default `http://localhost:8083`) |

Los endpoints `/internal/**` no se exponen por el gateway.

## Identidad

- Las lecturas públicas no requieren headers.
- Las rutas protegidas requieren `X-User-Id` (UUID) y `X-User-Role` (`CLIENT`, `ORGANIZER` o `ADMIN`).
- Sin headers válidos el gateway responde `401` en formato RFC 7807.
- El gateway agrega `X-Correlation-Id` si el cliente no lo envía.

En la Entrega 2 el gateway valida JWT y sigue propagando los mismos headers; los servicios no cambian.

## Ejecutar y probar

```bash
cd api-gateway
mvn test
mvn spring-boot:run
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/catalog/venues
```

Swagger de cada servicio a través del gateway: `http://localhost:8080/api/<servicio>/swagger-ui.html` no está expuesto; se usa el puerto interno del servicio.
