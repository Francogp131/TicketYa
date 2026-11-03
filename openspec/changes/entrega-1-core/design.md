# Design

## Context

TicketYa es un proyecto greenfield: hoy solo existen el enunciado, las features/user stories y el change de OpenSpec. La Entrega 1 debe cubrir publicación, búsqueda, compra con reserva temporal, no doble venta, devoluciones y cancelación, sin autenticación (usuario por encabezados) y con ejecución local. Ver `proposal.md` para el alcance y `specs/` para los requisitos.

Restricciones que condicionan el diseño:

- Cuatro proyectos Maven independientes: `catalog-service`, `inventory-service`, `orders-service`, `api-gateway`.
- Una base relacional por microservicio; en esta entrega las bases se levantan con Docker.
- Un solo punto de entrada: el gateway.
- La doble venta debe quedar impedida aunque haya varias instancias y reintentos de pago.
- El equipo tiene cuatro personas trabajando en paralelo y una fecha límite corta, por lo que los contratos deben congelarse temprano.

## Goals / Non-Goals

**Goals:**

- Definir límites de servicio, dueño de cada dato y contrato REST que permitan trabajar en paralelo sin bloquearse.
- Resolver la consistencia entre servicios con una orquestación simple y compensaciones explícitas, suficiente para la Entrega 1.
- Garantizar la no doble venta con el motor de base de datos como árbitro, no con estado en memoria.
- Dejar la puerta abierta a la Entrega 2 (Saga/outbox, JWT, Redis, NoSQL, Docker por servicio) sin rehacer el modelo.

**Non-Goals:**

- Fila virtual, control de acceso por QR, recomendaciones, notificaciones, reportes, JWT, MongoDB, cache, log centralizado y docker-compose de todos los servicios (Entrega 2).
- Interfaz gráfica.
- Alta disponibilidad real o despliegue en la nube.
- Mensajería asíncrona y Saga formal; en esta entrega se usa REST síncrono con reintentos y compensaciones.

## Decisions

### 1. Límites de servicio y dueño del dato

| Servicio | Responsabilidad | Datos propios |
|---|---|---|
| `catalog-service` | Lugares, sectores, asientos, eventos, precios, publicación, búsqueda y estado del evento | `lugar`, `sector`, `asiento`, `evento`, `evento_sector_precio`, `evento_participante` |
| `inventory-service` | Disponibilidad, reservas temporales, venta, liberación y límite por cliente | `evento_inventario`, `sector_inventario`, `asiento_inventario`, `reserva`, `reserva_item`, `cliente_evento` |
| `orders-service` | Órdenes, pagos, entradas/QR, devoluciones y reintegros | `orden`, `orden_item`, `pago`, `entrada`, `devolucion`, `idempotency_key` |
| `api-gateway` | Ruteo, propagación de identidad y punto de entrada | Sin datos |

Regla: ningún servicio lee ni escribe las tablas de otro. Todo cruce es por REST o por un ID copiado como referencia.

**Alternativas consideradas:** un servicio monolítico con módulos (más simple, pero incumple el requisito de microservicios independientes); un servicio de disponibilidad dentro de catalog (mezcla lectura de catálogo con escritura transaccional de asientos y complica el aislamiento).

### 2. Stack y persistencia

- Java 21 + Spring Boot 3.x + Maven, un `pom.xml` por servicio, sin parent obligatorio para que cada proyecto se construya y ejecute por separado.
- PostgreSQL 16, una base por servicio (`catalog_db`, `inventory_db`, `orders_db`) y un contenedor por base en `docker-compose.dev.yml` (puertos 5433, 5434, 5435). Flyway para esquema y datos de prueba.
- `springdoc-openapi` para Swagger UI y exportación de `openapi.json`.
- JUnit 5 + Mockito para unitarios; Spring Boot Test + Testcontainers (PostgreSQL real) para integración y concurrencia. No se usa H2 para concurrencia: su semántica de bloqueo no representa a PostgreSQL.
- Gateway con Spring Cloud Gateway (WebFlux).

**Alternativas consideradas:** MySQL (válido, pero PostgreSQL ofrece `RETURNING`, `ON CONFLICT` y `FOR UPDATE` cómodos para reservas atómicas); H2 en tests (descartado por falsear el comportamiento de concurrencia).

### 3. Contrato REST, errores e identidad

- Prefijo público por servicio a través del gateway: `/api/catalog/**`, `/api/inventory/**`, `/api/orders/**`.
- Endpoints internos: `/internal/**`, no expuestos por el gateway.
- IDs UUID; fechas ISO-8601 con offset `America/Argentina/Buenos_Aires`; montos `BigDecimal` en ARS.
- Errores RFC 7807 (`application/problem+json`) con `status`, `title`, `detail`, `instance`, `code` de negocio y `errors[]` para validaciones.
- Identidad de Entrega 1: el gateway exige `X-User-Id` y `X-User-Role` en endpoints protegidos y los propaga. Header ausente → 401; rol no permitido → 403. En Entrega 2 el gateway valida JWT y sigue propagando los mismos headers, por lo que los servicios no cambian.
- Contrato congelado en `docs/api-contract.md` el Día 0 y reflejado en OpenAPI; cualquier cambio de contrato se discute en el PR y se actualiza el spec.

**Alternativas consideradas:** validar identidad en cada servicio (duplica lógica y dificulta el reemplazo por JWT); un BFF por cliente (fuera de alcance de la Entrega 1).

### 4. Modelo de datos y datos replicados

- `catalog-service` es dueño de la definición de lugares, sectores, asientos y precios.
- Al publicar, catalog envía a inventory un snapshot: `eventoId`, fecha, sectores (`sectorId`, tipo, precio, capacidad) y asientos (`asientoId`, fila, número). Inventory guarda ese snapshot como read model para reservar sin depender de catalog en el camino crítico.
- `orders-service` guarda un snapshot de `eventoId`, `eventoNombre`, `eventoFecha`, `sectorId`, `asientoId` y precio unitario al crear la orden. Así puede validar devoluciones (72 h) y armar reportes sin consultar catalog.
- Entidades solapadas y su dueño:

| Dato | Dueño | Copias |
|---|---|---|
| Evento (nombre, fecha, estado) | catalog | inventory (fecha), orders (snapshot en orden/entrada) |
| Sector y asiento (definición) | catalog | inventory (IDs y precio), orders (snapshot en ítem/entrada) |
| Precio | catalog | inventory (snapshot al publicar), orders (snapshot al comprar) |
| Disponibilidad y estado de asiento | inventory | catalog solo la consulta para el detalle; no la persiste |
| Entrada y QR | orders | nadie más |

Los precios no cambian después de publicar (spec), por lo que el snapshot es estable. Si en el futuro se permiten cambios, se versiona el precio.

**Alternativas consideradas:** que inventory consulte catalog en cada reserva (acopla la disponibilidad a la latencia y disponibilidad de catalog); que orders consulte catalog al comprar (mismo problema y rompe la compra si catalog cae).

### 5. Publicación y sincronización de estados

Flujo de publicación:

1. Catalog valida que el evento esté `BORRADOR`, sea del organizador y que todos los sectores tengan precio.
2. Catalog llama `POST /internal/events/{id}/inventory` con el snapshot y una clave de idempotencia (`eventoId` + versión). Inventory hace upsert idempotente.
3. Solo si inventory responde 2xx, catalog pasa el evento a `PUBLICADO`. Si falla, el evento queda `BORRADOR` y se devuelve 502/503 para reintentar.

Sincronización de disponibilidad:

- Inventory llama `PATCH /internal/events/{id}/availability` cuando la disponibilidad llega a 0 (→ `AGOTADO`) o vuelve a ser positiva (→ `PUBLICADO`). Reintenta con backoff corto; si falla, catalog reconcilia al consultar el detalle.
- El detalle de evento consulta la disponibilidad real a inventory; la búsqueda usa el estado persistido (eventual consistencia aceptada y documentada).
- Un scheduler de catalog pasa a `FINALIZADO` los eventos publicados cuya fecha y hora ya pasaron.

**Alternativas consideradas:** outbox + mensajería (correcto para Entrega 2, pero suma infraestructura y tiempo); que catalog sea la única fuente de disponibilidad (no soporta concurrencia transaccional de asientos).

### 6. Reserva y anti doble venta

Inventory es el único que escribe disponibilidad. Todas las operaciones son transaccionales y condicionales:

- **Asientos numerados:** dentro de una transacción, `SELECT ... FOR UPDATE` de los asientos ordenados por ID (evita deadlocks), validando que todos estén `LIBRE`; si falta alguno, rollback y 409. Luego se actualizan a `RESERVADO` con `reserva_id` y `expires_at`. El lock del motor serializa a las instancias.
- **Zonas no numeradas:** `UPDATE sector_inventario SET disponibles = disponibles - N WHERE evento_id = ? AND sector_id = ? AND disponibles >= N RETURNING disponibles`. Si no hay fila, 409 sin cambios.
- **Límite por cliente:** `pg_advisory_xact_lock(hashtext(eventoId || clienteId))` y luego se cuentan entradas vendidas + reservas vigentes. El advisory lock evita que dos reservas simultáneas del mismo cliente pasen el límite.
- **Vencimiento:** scheduler cada 30 s marca reservas vencidas y libera sus ítems con updates condicionales (`WHERE estado = 'RESERVADO' AND reserva_id = ?`). Es idempotente y seguro con varias instancias. Además, confirmar una reserva valida `expires_at` en la misma transacción (pago vencido → 410).
- **Confirmación:** `UPDATE reserva SET estado = 'CONFIRMADA' WHERE id = ? AND estado = 'VIGENTE' AND expires_at > now()`; si afecta 0 filas → 409/410. Asientos `RESERVADO` → `VENDIDO`. Si la reserva ya estaba `CONFIRMADA`, se devuelve el mismo resultado (idempotente).

**Alternativas consideradas:** locks en memoria o `synchronized` (no funcionan con varias instancias); Redis como lock distribuido (suma infraestructura y no es necesario: PostgreSQL ya garantiza atomicidad); optimistic locking con `@Version` (válido, pero los updates condicionales son más directos y generan menos reintentos).

### 7. Saga de compra y compensaciones

La orquestación vive en `orders-service` (coreografía simple, sin framework de Saga):

1. Cliente reserva en inventory y recibe `reservaId` + `expiresAt`.
2. `POST /api/orders` con `Idempotency-Key`, `reservaId` y medio de pago. Orders consulta la reserva, valida dueño/vigencia y crea la orden `PENDIENTE_PAGO` con total calculado en el servidor.
3. Orders llama a la pasarela mock (timeout 5 s).
4. Pago aprobado → orders confirma la reserva en inventory (idempotente) → marca asientos vendidos → emite una entrada con QR único (UUID) por lugar → orden `PAGADA`.
5. Pago rechazado → orders libera la reserva → orden `RECHAZADA`, sin entradas.
6. Si la confirmación de inventory falla después del pago → orders solicita reintegro a la pasarela, libera la reserva y deja la orden `RECHAZADA`/`ERROR_PAGO` (nunca `PAGADA`).
7. Si falla la emisión de entradas → se liberan los lugares, se solicita reintegro y no queda ninguna entrada válida.

Cada llamada interna lleva clave de idempotencia (orden + paso) para que los reintentos no dupliquen efectos. Los estados `ERROR_PAGO` y `REINTEGRO_PENDIENTE` permiten reintentar la compensación sin perder trazabilidad.

**Alternativas consideradas:** transacción distribuida 2PC (no soportada por los servicios y sobredimensionada); coreografía con eventos (Entrega 2). Para la Entrega 1, la orquestación síncrona con compensaciones explícitas es defendible y testeable.

### 8. Idempotencia de compra

Tabla `idempotency_key` en orders: `key` única, `request_hash`, `response_status`, `response_body`, `estado` (`EN_PROCESO`, `COMPLETADA`), `created_at`, `expires_at` (24 h).

- Al recibir la compra: insertar con `ON CONFLICT DO NOTHING`.
- Si se insertó, procesar y guardar la respuesta.
- Si ya existía: mismo hash → devolver la respuesta guardada; hash distinto → 422; `EN_PROCESO` → 409 con `Retry-After`.
- La clave cubre creación de orden y pago; el reintento no genera otra orden ni otro cobro.

**Alternativas consideradas:** idempotencia solo por `reservaId` (no cubre reintentos con otra reserva ni cambios de cuerpo); deduplicación en memoria (se pierde al reiniciar y no sirve multi-instancia).

### 9. Pasarela de pagos mock

Adapter `PaymentGateway` con implementación mock:

- `POST /internal/payments` con `{ordenId, monto, medioPago, token}`.
- Token `APROBADO` → aprobado; `RECHAZADO` → rechazado; `LENTO` → demora configurable (para probar timeout); cualquier otro → rechazado.
- `POST /internal/payments/{id}/refund` idempotente por `paymentId`.
- La interfaz permite reemplazar el mock por una pasarela real sin tocar la lógica de órdenes.

### 10. Devoluciones y cancelación

- Devolución: orders valida dueño, estado `VALIDA`, sin devolución pendiente y `eventoFecha - now() > 72 h`; crea `PENDIENTE`.
- Aprobación (admin): estado `PROCESANDO` → inventory libera el asiento/zona (idempotente) → entrada `DEVUELTA` → refund mock → `APROBADA`. Si inventory falla, queda `PROCESANDO` y se reintenta; nunca se marca aprobada sin liberar.
- Rechazo: `RECHAZADA`; la entrada sigue `VALIDA`.
- Cancelación de evento: catalog pasa a `CANCELADO` y llama `POST /internal/events/{id}/cancel` a orders. Orders marca todas las entradas vendidas `EN_REINTEGRO` y solicita refunds. Si la llamada falla, catalog guarda la cancelación pendiente y un scheduler reintenta; el endpoint es idempotente.

**Alternativas consideradas:** que orders detecte la cancelación por polling (más lento y costoso); que catalog llame a inventory para liberar (innecesario: el evento cancelado no vuelve a venderse).

### 11. Testing y documentación

- Unitarios por servicio: reglas de estado, precios, límites, devoluciones, cálculo de totales.
- Integración con Testcontainers: publicación → inventario, compra completa, pago rechazado, vencimiento, devolución, cancelación.
- Concurrencia: 100 hilos contra PostgreSQL real, mismo asiento y último lugar de zona; se afirma 1 éxito y 99 conflictos, y que la base tenga una sola reserva/venta.
- OpenAPI por servicio con ejemplos y códigos de error; Postman con flujos y assertions; datos de prueba por Flyway.
- Diagramas en Mermaid versionados en `docs/`: módulos, secuencia de compra (aprobado/rechazado/vencido) y ER por servicio.

## Risks / Trade-offs

- **Consistencia eventual entre catalog e inventory** → publicación solo confirma si inventory aceptó; callbacks con reintento y reconciliación en el detalle. La búsqueda puede mostrar `PUBLICADO` unos segundos de más.
- **Deadlocks en reservas multi-asiento** → ordenar IDs y tomar locks en el mismo orden; reintentar la transacción ante deadlock (máximo 3 intentos).
- **Datos replicados (precio/fecha) quedan obsoletos** → solo se replican datos inmutables después de publicar; se documenta y se versiona si cambia la regla.
- **Scheduler duplicado en varias instancias** → updates condicionales e idempotentes; no se depende de “una sola instancia corre el job”.
- **Fallo parcial de la saga deja estados intermedios** → estados `ERROR_PAGO`/`REINTEGRO_PENDIENTE` + reintentos idempotentes; en Entrega 2 se reemplaza por outbox/Saga.
- **Tiempo del equipo** → contrato congelado el Día 0, tareas por servicio y PRs diarios; si hay atraso se recorta relevancia/orden secundario, nunca la no doble venta ni el flujo de compra.
- **Tests de concurrencia dependen de Docker** → CI con Docker y Testcontainers; si el entorno local no lo soporta, se corre en CI antes de cada merge.

## Migration Plan

Proyecto nuevo, sin migración de datos productivos:

1. Levantar las tres bases con `docker compose -f docker-compose.dev.yml up -d` y aplicar Flyway.
2. Desplegar `catalog-service`, `inventory-service` y `orders-service`; verificar Actuator.
3. Levantar `api-gateway` y verificar el ruteo a los tres servicios.
4. Cargar datos de prueba (Flyway seed) y ejecutar el smoke test de Postman.
5. Rollback de desarrollo: `docker compose down -v` y recrear; no hay datos que preservar en la Entrega 1.

## Open Questions

Ninguna que cambie el alcance o los specs. Quedan para la Entrega 2: elección de MongoDB para recomendaciones, Redis para fila virtual, formato de JWT y estrategia de Saga/outbox.
