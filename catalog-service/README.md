# catalog-service

Servicio de catálogo de TicketYa: lugares, sectores, asientos, eventos, precios, publicación, búsqueda y estado del evento.

- **Puerto:** 8081
- **Base:** `catalog_db` (PostgreSQL 16, puerto 5433)
- **Responsable:** P1 — ver [`docs/equipo/compa-1.md`](../docs/equipo/compa-1.md)
- **Contrato:** [`docs/api-contract.md`](../docs/api-contract.md), sección 3.

## Endpoints implementados (Día 1)

| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| POST | `/api/catalog/venues` | ADMIN | Crea un lugar |
| GET | `/api/catalog/venues` | público | Lista lugares paginados |
| GET | `/api/catalog/venues/{venueId}` | público | Detalle de un lugar |
| POST | `/api/catalog/venues/{venueId}/sectors` | ADMIN | Crea un sector y genera sus asientos si es numerado |
| GET | `/api/catalog/venues/{venueId}/sectors` | público | Lista sectores del lugar |
| POST | `/api/catalog/events` | ORGANIZER | Crea un evento en `BORRADOR` |
| PUT | `/api/catalog/events/{eventoId}/prices/{sectorId}` | ORGANIZER | Asigna el precio de un sector |
| POST | `/api/catalog/events/{eventoId}/publish` | ORGANIZER | Publica y envía el snapshot a inventory |
| GET | `/api/catalog/events/{eventoId}` | público | Detalle con disponibilidad consultada a inventory |

Los endpoints de búsqueda (`GET /api/catalog/events` con filtros), cancelación y scheduler de finalización corresponden a las tareas 2.3–2.6 (Días 2–4).

## Modelo

Tablas Flyway en [`src/main/resources/db/migration`](src/main/resources/db/migration):

- `lugar` — nombre, dirección, ciudad, latitud/longitud.
- `sector` — pertenece a un lugar; `NUMERADO` exige filas y asientos por fila.
- `asiento` — se genera al crear un sector numerado (`filas × asientosPorFila`).
- `evento` — estado `BORRADOR/PUBLICADO/AGOTADO/CANCELADO/FINALIZADO`, organizador y límite por cliente.
- `evento_participante` y `evento_sector_precio` — participantes y precio por sector.

Al publicar, el servicio envía a inventory el snapshot del evento (sectores, precios, capacidad y asientos) por `POST /internal/events/{id}/inventory`. Si inventory falla, el evento sigue en `BORRADOR` y se responde `502 INVENTORY_UNAVAILABLE`.

## Variables de entorno

| Variable | Default | Uso |
|---|---|---|
| `SERVER_PORT` | `8081` | Puerto HTTP |
| `DB_URL` | `jdbc:postgresql://localhost:5433/catalog_db` | Conexión JDBC |
| `DB_USER` / `DB_PASSWORD` | `ticketya` | Credenciales |
| `INVENTORY_URL` | `http://localhost:8082` | Cliente interno hacia inventory |

## Ejecutar y probar

```bash
docker compose -f docker-compose.dev.yml up -d catalog-db
cd catalog-service
mvn test
mvn spring-boot:run
curl http://localhost:8081/actuator/health
```

Swagger UI: <http://localhost:8081/swagger-ui.html> · OpenAPI: <http://localhost:8081/v3/api-docs>

`mvn test` corre los tests unitarios y de controller sin Docker. `VenueServiceIntegrationTest`
valida la generación de asientos contra PostgreSQL 16 real con Testcontainers; si Docker no está
disponible el test se omite, y en CI se ejecuta siempre porque el runner lo tiene.
