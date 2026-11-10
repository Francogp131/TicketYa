# TicketYa

TP grupal de Arquitectura Web (TUDAI 2026): plataforma de venta de entradas con microservicios Spring Boot.
Enunciado: [`docs/TPE-ArqWeb-TicketYa-2026.pdf`](docs/TPE-ArqWeb-TicketYa-2026.pdf).

Repositorio del curso: <https://github.com/Pabloaguirre23/ArquitecturaWeb> (este proyecto vive en `TP-TicketYa/`, rama `main`).

## Arquitectura

| Componente | Puerto | Base | Responsabilidad |
|---|---|---|---|
| `api-gateway` | 8080 | — | Punto de entrada único, ruteo e identidad |
| `catalog-service` | 8081 | `catalog_db` (5433) | Lugares, sectores, eventos, precios, publicación y búsqueda |
| `inventory-service` | 8082 | `inventory_db` (5434) | Disponibilidad, reservas temporales y no doble venta |
| `orders-service` | 8083 | `orders_db` (5435) | Órdenes, pago mock, entradas/QR, devoluciones y cancelación |

Cada servicio es un proyecto Maven independiente. La comunicación entre servicios es REST por `/internal/**`; el cliente siempre entra por el gateway.

## Requisitos

- **Java 21** (Temurin recomendado)
- **Maven 3.9+**
- **Docker Desktop** (para PostgreSQL 16)
- Git

Verificar:

```bash
java -version          # debe decir 21
mvn -version           # debe usar Java 21
docker --version
```

Si Maven usa otra versión de Java, configurá `JAVA_HOME` apuntando a tu JDK 21. En macOS:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

## Cómo ejecutar

### 1. Bases de datos

```bash
docker compose -f docker-compose.dev.yml up -d
docker compose -f docker-compose.dev.yml ps
```

Las tres bases deben quedar `healthy`. Flyway aplica las migraciones al levantar cada servicio.

Para reiniciar sin datos:

```bash
docker compose -f docker-compose.dev.yml down -v
docker compose -f docker-compose.dev.yml up -d
```

### 2. Servicios

En tres terminales distintas:

```bash
cd catalog-service && mvn spring-boot:run
cd inventory-service && mvn spring-boot:run
cd orders-service && mvn spring-boot:run
```

Verificar:

```bash
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
```

### 3. Gateway

```bash
cd api-gateway && mvn spring-boot:run
curl http://localhost:8080/actuator/health
```

### 4. Swagger

- Catalog: <http://localhost:8081/swagger-ui.html>
- Inventory: <http://localhost:8082/swagger-ui.html>
- Orders: <http://localhost:8083/swagger-ui.html>

OpenAPI crudo: `http://localhost:<puerto>/v3/api-docs`.

## Variables de entorno

Los servicios traen valores por defecto para desarrollo. Se pueden pisar con:

| Variable | Default | Uso |
|---|---|---|
| `SERVER_PORT` | 8081/8082/8083/8080 | Puerto del servicio |
| `DB_URL` | `jdbc:postgresql://localhost:<5433/5434/5435>/<db>` | Conexión JDBC |
| `DB_USER` | `ticketya` | Usuario de la base |
| `DB_PASSWORD` | `ticketya` | Password de la base |
| `CATALOG_URL` | `http://localhost:8081` | Gateway → catalog |
| `INVENTORY_URL` | `http://localhost:8082` | Gateway → inventory |
| `ORDERS_URL` | `http://localhost:8083` | Gateway → orders |
| `CATALOG_FINALIZACION_MS` | `60000` | Frecuencia del job de eventos vencidos |
| `CATALOG_CANCELACIONES_MS` | `60000` | Frecuencia del reintento de cancelaciones |
| `INVENTORY_VENCIMIENTO_MS` | `30000` | Frecuencia del job de reservas vencidas |

Ver [`.env.example`](.env.example). No se commitea `.env`.

## Prueba rápida del gateway

Las lecturas públicas no piden identidad:

```bash
curl http://localhost:8080/api/catalog/events
```

Las rutas protegidas piden `X-User-Id` y `X-User-Role`:

```bash
curl -X POST http://localhost:8080/api/inventory/reservations \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e" \
  -H "X-User-Role: CLIENT" \
  -d '{"eventoId":"...","items":[]}'
```

Sin headers, el gateway responde 401 en formato RFC 7807. El contrato completo está en [`docs/api-contract.md`](docs/api-contract.md).

## Estructura

| Carpeta | Contenido |
|---|---|
| `catalog-service/` | Eventos, lugares, sectores, búsqueda |
| `inventory-service/` | Reservas y anti doble venta |
| `orders-service/` | Compra, pago mock, QR, devoluciones |
| `api-gateway/` | Punto de entrada único |
| `openspec/` | Especificaciones (propuesta → specs → diseño → tareas) |
| `docs/` | Contrato, plan, checklist, arquitectura y briefs del equipo |
| `docker-compose.dev.yml` | PostgreSQL 16 por servicio |

## Tests

Cada servicio se testea por separado:

```bash
cd catalog-service && mvn test
cd inventory-service && mvn test
cd orders-service && mvn test
cd api-gateway && mvn test
```

Las pruebas de integración de catalog, concurrencia de inventory e idempotencia de orders usan Testcontainers y requieren Docker.

## Equipo y flujo de trabajo

- [Briefs por integrante](docs/equipo/README.md)
- [Cómo trabajamos](CONTRIBUTING.md)
- [Plan día por día — Entrega 1](docs/plan-entrega-1.md)
- [Checklist verificable de la Entrega 1](docs/checklist-entrega-1.md)
- [Índice de documentación](docs/README.md)
- [Cambio OpenSpec de la Entrega 1](openspec/changes/entrega-1-core/)

Reglas:

1. `main` protegida; todo entra por Pull Request con al menos 1 aprobación.
2. Un dueño por carpeta de microservicio.
3. Rama por tarea: `feat/<servicio>-<tema>`.
4. La documentación y los tests van en el mismo PR que el código.

## Metodología

Desarrollo guiado por especificaciones con [OpenSpec](https://github.com/Fission-AI/OpenSpec). El change activo es `entrega-1-core`:

```bash
openspec validate entrega-1-core
openspec status --change entrega-1-core
```

## Estado

Entrega 1: núcleo implementado (publicación, búsqueda, compra con reserva y QR, no doble
venta, devoluciones y cancelación). El estado verificable y las observaciones abiertas están
en [`docs/checklist-entrega-1.md`](docs/checklist-entrega-1.md); la estrategia y la evidencia
de pruebas, en [`docs/pruebas.md`](docs/pruebas.md).

Entrega: <https://github.com/Pabloaguirre23/ArquitecturaWeb/tree/main/TP-TicketYa>

## Datos de prueba

Los seeds de Flyway (`V2__seed.sql` en catalog e inventory) crean:

| Dato | ID | Detalle |
|---|---|---|
| Lugar | `a0000000-0000-4000-8000-000000000001` | Estadio Municipal de Tandil |
| Lugar | `a0000000-0000-4000-8000-000000000002` | Complejo Cultural Universitario |
| Evento | `d0000000-0000-4000-8000-000000000001` | Festival de Rock Tandil 2027, 14/03/2027 |
| Evento | `d0000000-0000-4000-8000-000000000002` | El Avaro — Teatro Universitario, 18/04/2027 |
| Evento | `d0000000-0000-4000-8000-000000000003` | Arquitectura Web 2027 — Charla abierta, 20/05/2027 |

Identidades de la Entrega 1 (el gateway las propaga como headers):

| Rol | `X-User-Id` |
|---|---|
| ADMIN | `11111111-1111-4111-8111-111111111111` |
| ORGANIZER | `22222222-2222-4222-8222-222222222222` |
| CLIENT | `33333333-3333-4333-8333-333333333333` |

Los asientos numerados usan IDs determinísticos: `c0000000-0000-4000-8000-[sector][fila][numero]000000`. La colección [`postman/TicketYa.postman_collection.json`](postman/TicketYa.postman_collection.json) recorre los flujos de catálogo y compra (aprobada, rechazada, pago lento y cancelación) y guarda los IDs que crea.
