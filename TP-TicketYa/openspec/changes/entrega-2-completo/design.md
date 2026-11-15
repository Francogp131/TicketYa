# Design

## Context

La Entrega 1 dejó cuatro proyectos Maven independientes (`catalog-service`, `inventory-service`, `orders-service`, `api-gateway`) con PostgreSQL por servicio, REST síncrono, identidad por encabezados, pasarela de pagos mock y una orquestación de compra con compensaciones. El núcleo ya resuelve publicación, búsqueda, reserva temporal, no doble venta, QR, devoluciones y cancelación.

La Entrega 2 debe completar los ítems 6, 8, 10, 11 y 12 del enunciado y los requisitos no funcionales: JWT con roles, al menos una base NoSQL, consistencia entre servicios, resiliencia, cache, bitácora/log centralizado, comunicación asíncrona justificada, un contenedor por servicio y `docker-compose.yml` integral. Ver `proposal.md` para el alcance y `specs/` para el comportamiento exigido.

Restricciones que condicionan el diseño:

- Cinco personas, diez días y una base de código que ya funciona; no se puede rehacer el núcleo.
- Cada servicio sigue siendo un proyecto Maven independiente, sin parent obligatorio.
- La no doble venta y la idempotencia de la compra no se negocian.
- El equipo debe poder levantar todo con un comando y demostrar cada requisito con evidencia.
- La interfaz gráfica sigue fuera del alcance comprometido.

## Goals / Non-Goals

**Goals:**

- Agregar fila virtual, acceso QR, recomendaciones, notificaciones y reportes sin romper el flujo de compra existente.
- Introducir JWT, MongoDB, Redis, RabbitMQ, cache, resiliencia, outbox y observabilidad con límites de servicio claros.
- Mantener una historia de migraciones Flyway incremental y un contrato REST versionado.
- Dejar evidencia reproducible de integración, seguridad y carga.

**Non-Goals:**

- Interfaz gráfica, aplicación móvil, pasarela de pagos real y proveedor real de email/push.
- Fila virtual en tiempo real estricto con WebSockets; se usa polling con tandas.
- Recomendaciones con machine learning; se usa un ranking explicable por reglas y co-compra.
- Alta disponibilidad, Kubernetes, multi-región y despliegue en nube.
- Event sourcing completo o transacciones distribuidas 2PC.
- BFF dedicado y Service Discovery (Eureka/Consul); se justifican como no-goals en
  [`docs/decisiones.md`](../../../docs/decisiones.md) (ADR-014 y ADR-015). El gateway y el
  DNS de Compose cubren el alcance.

## Decisions

### 1. Límites de servicio y nuevos componentes

| Componente | Responsabilidad | Datos propios |
|---|---|---|
| `api-gateway` | Punto de entrada, login, validación JWT, autorización por rol, correlación y ruteo | Usuarios y credenciales (tabla `usuario`) |
| `catalog-service` | Lugares, eventos, precios, publicación, búsqueda, cache, alta demanda y datos de reporte | `catalog_db` + cache Redis |
| `inventory-service` | Disponibilidad, reservas, no doble venta, validación de token de fila | `inventory_db` |
| `orders-service` | Órdenes, pagos, entradas/QR, devoluciones, acceso, reportes, saga y outbox | `orders_db` |
| `queue-service` | Fila virtual, posiciones, tandas y tokens de compra | Redis (almacén primario) |
| `recommendation-service` | Preferencias, historial y recomendaciones | MongoDB `recommendation_db` |
| `notification-service` | Consumo de eventos, envío simulado, reintentos y DLQ | `notification_db` |
| `payment-mock` | Pasarela simulada existente dentro de orders | Sin datos propios |
| Observabilidad | Logs JSON, Loki, Promtail y Grafana | Volúmenes de Loki/Grafana |

Regla: ningún servicio lee ni escribe las tablas o colecciones de otro. Los cruces son REST por `/internal/**` o eventos de dominio.

**Alternativas consideradas:** un `auth-service` separado (más “microservicio”, pero suma un despliegue y una base para una tabla pequeña; el gateway ya es el borde de seguridad y puede emitir el token); un `report-service` separado (los datos de ventas ya viven en orders y el cálculo es acotado); un `access-service` separado (el dueño del QR es orders y separarlo obligaría a duplicar la tabla de entradas).

### 2. Seguridad: JWT en el gateway y roles en los servicios

- `api-gateway` incorpora Spring Security WebFlux y JJWT. `POST /api/auth/login` valida email y contraseña BCrypt contra la tabla `usuario` de `auth_db` (misma base del gateway) y emite un JWT HS256 con `sub`, `role`, `email`, `exp` (8 h) y `jti`. El acceso a `auth_db` no bloquea el event loop: R2DBC (opción preferida) o `JdbcTemplate` dentro de `boundedElastic` solo en el login; ver ADR-016. No se agrega JPA al gateway y el JWT se valida en memoria, sin consultar la base por request.
- El filtro de seguridad valida la firma y la expiración, aplica una matriz de rutas por método y rol, elimina cualquier `X-User-*` enviado por el cliente y propaga `X-User-Id`, `X-User-Role`, `X-User-Email` y `X-Correlation-Id`.
- Los roles son `CLIENT`, `ORGANIZER`, `ADMIN` y `ACCESS_STAFF`. La matriz es *default deny*: una ruta nueva sin regla no es accesible hasta declararla.
- Los servicios confían en los encabezados del gateway, pero igualmente exigen identidad en endpoints protegidos y aplican la regla de negocio (por ejemplo, dueño de la orden). Los endpoints `/internal/**` exigen `X-Internal-Token`; el gateway borra ese encabezado del tráfico externo.
- Las contraseñas se siembran con BCrypt y se documentan usuarios de prueba por rol. El secreto JWT y el token interno se leen de variables de entorno; en desarrollo hay defaults y en producción no se commitean.
- El token de fila virtual es un JWT separado, firmado con `QUEUE_TOKEN_SECRET`, con `eventId`, `sub`, `jti`, `exp` corto (5 min) y `type=QUEUE`. inventory-service lo valida localmente para no agregar una llamada síncrona en el camino crítico.

**Alternativas consideradas:** sesiones con cookie (más estado y menos adecuado para clientes móviles); OAuth2/Keycloak (sobredimensionado para el TP); validar JWT en cada servicio (duplica configuración y claves). El gateway sigue siendo el único punto que emite y valida el token de usuario; el token de fila se valida también en inventory porque es una credencial acotada.

### 3. MongoDB para preferencias e historial

`recommendation-service` usa MongoDB porque:

- Las preferencias son documentos con listas variables (`tipos`, `artistas`, `ciudad`) y evolucionan sin migraciones rígidas.
- El historial de compras crece por cliente y se consulta con agregaciones por `eventoId`, `clienteId`, tipo, artista y ciudad.
- El esquema de recomendaciones puede incorporar nuevos campos (pesos, motivos, última actualización) sin alterar tablas.

Colecciones e índices:

| Colección | Documento | Índices |
|---|---|---|
| `preferencias` | `{clienteId, tipos[], artistas[], ciudad, updatedAt}` | único `clienteId`; `ciudad` |
| `historial_compras` | `{eventoId, clienteId, tipo, artistas[], ciudad, fechaEvento, ordenId, eventId}` | único `(eventId, clienteId, eventoId)`; `(clienteId, fechaEvento)`; `(eventoId, clienteId)` |
| `eventos_vistos` | snapshot mínimo para fallback y motivos | `eventoId` único; `ciudad`, `tipo` |

El historial se alimenta con `orden.pagada`; el consumo es idempotente por `eventId`. Las recomendaciones combinan preferencias (tipo, artista, ciudad) y co-compra: clientes que compraron el evento X también compraron Y, ordenado por frecuencia y proximidad de fecha. Sin historial, se devuelven eventos populares de la ciudad.

**Alternativas consideradas:** PostgreSQL con JSONB (válido, pero el requisito pide al menos una NoSQL y MongoDB encaja mejor con documentos variables); Redis para preferencias (pierde consultas y agregaciones); un servicio de recomendaciones dentro de catalog (mezcla lectura de catálogo con historial transaccional y complica el aislamiento).

### 4. Fila virtual con Redis y token firmado

`queue-service` usa Redis como almacén primario:

- Por evento, un sorted set `queue:{eventoId}:waiting` con score = timestamp de ingreso + secuencia para desempatar, y un hash `queue:{eventoId}:state` con estado de la fila, tamaño de tanda, intervalo y versión.
- `POST /api/queue/events/{eventId}/join` usa `ZADD NX` para que el reingreso conserve la posición; devuelve posición (`ZRANK + 1`), estado y estimación.
- Un scheduler por evento (o un job global cada N segundos) mueve hasta `batchSize` clientes de `waiting` a `admitted`, les asigna un `jti` y emite el token de fila. La operación es atómica con Lua o transacción Redis para no habilitar dos veces.
- `GET /api/queue/events/{eventId}/position` devuelve posición, estado y token si está habilitado. El cliente hace polling cada 2-5 s; se documenta la simplificación frente a WebSockets.
- El token se valida en inventory-service con la clave compartida; no se consulta queue-service en la reserva. Además se verifica que el `jti` no esté revocado en una lista corta en Redis (o se acepta la ventana de 5 min y se documenta).
- Al cancelar, finalizar o agotar el evento, se marca la fila `CERRADA` y se invalidan tokens nuevos. Los tokens ya emitidos vencen por TTL.
- Redis se configura con AOF para reducir la pérdida de estado. Si Redis no está disponible, los eventos de alta demanda no permiten reservar (fail closed); los eventos normales siguen funcionando.

**Alternativas consideradas:** base relacional para la cola (más fricción para ordenar y expirar); locks distribuidos con Redisson (más dependencia); fila en memoria (no sirve multi-instancia); WebSockets (fuera de alcance y más complejo en el gateway).

### 5. Control de acceso con QR en orders-service

- `POST /api/orders/access/validate` recibe `qrCode` y `eventoId`, exige `ACCESS_STAFF` o `ADMIN` y ejecuta un `UPDATE entrada SET estado='UTILIZADA', used_at=now(), used_by=? WHERE qr_code=? AND evento_id=? AND estado='VALIDA' RETURNING ...`.
- Si el update afecta una fila, el ingreso es válido. Si afecta cero, se consulta la entrada para distinguir 404 (no existe), 409 (ya utilizada o estado no válido) y 409 (otro evento).
- El update condicional es la garantía de uso único, igual que la no doble venta; no se usa un lock en memoria.
- Cada intento se registra en `audit_log` con la correlación y el personal. No se guarda la imagen del QR.

**Alternativas consideradas:** un servicio de acceso separado (duplica el dueño de la entrada); validación en inventory (no es el dueño del QR); un `SELECT` seguido de `UPDATE` sin condición (vulnerable a dos escaneos simultáneos).

### 6. Notificaciones asíncronas con RabbitMQ

- Exchange `ticketya.events` de tipo topic. Routing keys: `evento.publicado`, `evento.actualizado`, `evento.cancelado`, `orden.pagada`, `orden.reintegrada`, `devolucion.aprobada`, `entrada.utilizada`.
- Colas: `notification.order`, `notification.event`, `notification.favorite`, `recommendation.history`; cada una con DLX/DLQ (`*.dlq`) y reintentos con backoff.
- El sobre versionado, los payloads y el fixture compartido están en [`docs/eventos.md`](../../../docs/eventos.md) (ADR-018).
- `notification-service` persiste `notificacion` (`id`, `destinatario`, `email`, `tipo`, `payload`, `estado`, `intentos`, `next_retry_at`, `created_at`, `sent_at`, `error`) en `notification_db`.
- El envío se hace detrás de una interfaz `NotificationSender` con implementación mock que loguea email/push y marca `ENVIADA`. Si falla, se reintenta hasta 5 veces con backoff; luego `FALLIDA` y a la DLQ.
- La confirmación de compra consume `orden.pagada`; la cancelación y el cambio consumen eventos de catálogo; el artista favorito consume `evento.publicado` y consulta preferencias a recommendation-service por `/internal/**` con timeout y fallback a no notificar.
- La compra nunca espera a notification-service: el producto publica el evento y sigue. Si el broker está caído, el outbox reintenta.

**Alternativas consideradas:** notificación síncrona dentro de orders (acopla la compra a un servicio accesorio y viola el requisito de no bloquear); un único consumidor para todo (mezcla dominios y dificulta reintentos); Kafka (más pesado para el TP; RabbitMQ alcanza y ya tiene DLQ).

### 7. Consistencia: saga orquestada, outbox y eventos idempotentes

- La compra sigue orquestada por orders-service, ahora con una máquina de estados explícita y una tabla `saga_compra` (`ordenId`, `paso`, `estado`, `intentos`, `ultimoError`, `updatedAt`). Pasos: `RESERVA_VALIDADA → ORDEN_CREADA → PAGO_APROBADO → INVENTARIO_CONFIRMADO → ENTRADAS_EMITIDAS`.
- Cada comando a inventory y a la pasarela lleva una clave de idempotencia (`ordenId:paso`). Los endpoints internos son idempotentes.
- Si falla un paso posterior al pago, se ejecuta la compensación en orden inverso: reintegro, liberación de inventario, orden `ERROR_PAGO`/`REINTEGRO_PENDIENTE`. Nunca se marca `PAGADA` sin entradas válidas.
- `catalog-service` y `orders-service` escriben un `outbox_event` en la misma transacción del cambio de negocio. Un publicador programado toma los pendientes con `FOR UPDATE SKIP LOCKED`, publica a RabbitMQ y marca `PUBLICADO`; si falla, reintenta con backoff. Esto da al menos una entrega y evita el dual-write.
- Los consumidores guardan `eventId` procesados en una tabla/colección de idempotencia y aplican el efecto una sola vez. Los eventos llevan `eventId`, `type`, `occurredAt`, `correlationId`, `version` y `payload`.
- `orden.pagada` lleva el snapshot del evento (`eventoTipo`, `artistas`, `ciudad`, `fechaEvento`) para que recommendation no dependa de catalog al construir el historial; ver ADR-017 y [`docs/eventos.md`](../../../docs/eventos.md).
- Consistencia: fuerte dentro de cada servicio (transacción local + constraints), eventual entre servicios. La disponibilidad y la orden se leen de su dueño; reportes y recomendaciones toleran retardo acotado. Se documenta en `docs/arquitectura.md`.

**Alternativas consideradas:** 2PC (no disponible entre servicios y sobredimensionado); event sourcing (cambia el modelo y el equipo no llega); coreografía pura (más difícil de depurar la compra); publicar directo a RabbitMQ dentro de la transacción (dual-write y eventos fantasma).

### 8. Resiliencia con Resilience4j

Se aplica en los clientes salientes, no en los endpoints internos:

| Dependencia | Timeout | Reintentos | Circuit breaker | Fallback |
|---|---|---|---|---|
| Pasarela de pagos | 5 s | Solo con clave de idempotencia, 2 | Sí | Orden reintentable, nunca `PAGADA` |
| inventory-service | 3 s | 2 en lecturas, 0 en confirmación | Sí | Fail closed; compensar |
| catalog-service (orders/recommendation) | 2 s | 2 en lecturas | Sí | Cache/última copia o 503 |
| queue-service | 2 s | 2 en lecturas | Sí | Fail closed para alta demanda |
| notification-service | — | 5 con backoff | Sí | Asíncrono, no bloquea |
| Redis (cache) | 1 s | 1 | Sí | Ir a la base |

Configuración por variables de entorno; métricas Micrometer y logs con la correlación. No se reintentan cobros sin clave de idempotencia. El circuit breaker se prueba con WireMock y un servidor lento.

**Alternativas consideradas:** Hystrix (obsoleto); reintentos infinitos (amplifican fallos); timeouts largos (consumen hilos y agotan el pool).

### 9. Cache de catálogo en Redis

- Se cachean: listado de lugares (TTL 5 min), detalle estático de evento (TTL 60 s) y resultados de búsqueda (TTL 30 s). El detalle estático excluye disponibilidad, asientos y estado transitorio; la disponibilidad se consulta siempre a inventory.
- Claves: `catalog:venue:{id}`, `catalog:event:{id}:static`, `catalog:search:{hash(params)}`. Se usa `@Cacheable(sync=true)` para evitar stampede.
- Invalidación: al publicar, editar, cancelar, cambiar precio o cambiar de estado se evictan las claves del evento y un prefijo de búsqueda. Si Redis falla, se evicta por TTL y se registra la degradación.
- No se cachean disponibilidad, mapa de asientos, reservas, órdenes, fila virtual ni reportes en tiempo real.
- Métricas de hit/miss/error y cabecera `X-Cache` para la demo.

**Alternativas consideradas:** cache local en cada instancia (no se invalida entre instancias); cache de disponibilidad (riesgo de vender de más); CDN (fuera de alcance).

### 10. Observabilidad: logs JSON, correlación y bitácora

- Todos los servicios usan Logback con `logstash-logback-encoder` y un patrón JSON con `service`, `level`, `timestamp`, `correlationId`, `userId`, `role`, `method`, `path`, `status`, `durationMs`, `message`.
- Un filtro en el gateway genera/valida `X-Correlation-Id`; un filtro en cada servicio lo toma (o genera uno si se llama directo en tests) y lo pone en MDC. Los clientes REST lo propagan en cada llamada.
- `audit_log` en catalog y orders: `id`, `operacion`, `entidad`, `entidadId`, `usuarioId`, `rol`, `resultado`, `detalle` JSONB, `correlationId`, `createdAt`. Se escribe en la misma transacción cuando es posible; si falla, no revierte la operación de negocio (se loguea).
- Stack: Loki + Promtail + Grafana en el compose. Promtail descubre contenedores por Docker socket y etiqueta `container`/`service`; Grafana tiene un datasource Loki provisionado. Se consulta por `{service="orders-service"} |= "<correlationId>"`.
- Retención de 7 días en desarrollo. No se loguean contraseñas, JWT completos, cuerpos de pago ni emails sin enmascarar.

**Alternativas consideradas:** ELK (más memoria y complejidad); solo `docker logs` (no es centralizado ni consultable); OpenTelemetry + Jaeger (valioso, pero el requisito pide log y el tiempo no alcanza para trazas completas).

### 11. Modelo de datos nuevo

Migraciones Flyway nuevas (nunca se edita una aplicada):

- `catalog_db`: `evento.alta_demanda`, `evento.updated_at` si falta, `outbox_event`, `audit_log`.
- `inventory_db`: `evento_inventario.alta_demanda`, `reserva.origen`/`queue_jti`, índices de vencimiento.
- `orders_db`: `orden.cliente_email`, `orden.evento_organizador_id`, `orden.sector_nombre` (snapshot), `orden.evento_tipo`, `orden.evento_artistas`, `orden.evento_ciudad` (snapshot para `orden.pagada`), `entrada.used_at`, `entrada.used_by`, `saga_compra`, `outbox_event`, `audit_log`, índices de reporte (`entrada(evento_id, sector_id, estado)`, `orden(evento_id, estado)`).
- `notification_db`: `notificacion`, `evento_procesado`.
- `auth_db`: `usuario` (id, email único, password_hash, role, activo).
- MongoDB: colecciones e índices de la decisión 3.
- Redis: no tiene migraciones; se documentan claves y TTL.

Entidades solapadas: evento y organizador se copian como snapshot en orders; capacidad y precios se consultan a catalog para reportes; preferencias viven solo en MongoDB. El detalle está en `docs/modelo-datos.md` (a actualizar en la tarea de documentación).

### 12. Despliegue con Docker

- Un Dockerfile multi-stage por servicio (`maven:3.9-eclipse-temurin-21` para build, `eclipse-temurin:21-jre-alpine` para runtime), usuario no root, `HEALTHCHECK` contra `/actuator/health`.
- `docker-compose.yml` integral con: `gateway`, `catalog`, `inventory`, `orders`, `queue`, `recommendation`, `notification`, `catalog-db`, `inventory-db`, `orders-db`, `notification-db`, `auth-db`, `redis`, `mongo`, `rabbitmq`, `loki`, `promtail`, `grafana`.
- Redes: `ticketya-net` interna; solo el gateway publica 8080. Las bases y brokers no publican puertos por defecto (se pueden exponer con un override de desarrollo). Volúmenes nombrados para persistencia.
- Healthchecks y `depends_on` con `condition: service_healthy`; RabbitMQ con management plugin; MongoDB sin autenticación en desarrollo y credenciales por entorno.
- `.env.example` con todas las variables; `.env` ignorado. `docker-compose.dev.yml` se mantiene para el flujo Maven local con PostgreSQL.
- CI: matriz de build/tests para los siete servicios, validación de `docker compose config`, job de integración con Docker y job de OpenAPI. La prueba de carga se corre manualmente contra el compose y se adjunta el informe.

**Alternativas consideradas:** imágenes con JAR precompilado (rápido, pero requiere build local y no es reproducible); Kubernetes (fuera de alcance); un compose por servicio (no cumple “un único compose”).

### 13. Testing

- Unitarios por servicio con JUnit 5 + Mockito.
- Integración por servicio con Testcontainers (PostgreSQL, Redis, MongoDB, RabbitMQ) para repositorios, cache, outbox y consumidores.
- Integración entre servicios: módulo `integration-tests` con Testcontainers Docker Compose o un perfil `e2e` que levanta el compose y ejecuta RestAssured/Newman. Flujos: login → buscar → fila → reservar → comprar → QR → reporte → recomendación → notificación.
- Seguridad: matriz endpoint × rol con MockMvc en gateway y servicios, más una carpeta Postman que verifica 401/403/200.
- Carga: JMeter sobre el flujo de compra (login, búsqueda, reserva, pago) con 500 y 1000 usuarios concurrentes; informe con throughput, p50/p95/p99, errores y cuello de botella.
- La prueba de no doble venta de la Entrega 1 se conserva y se corre en CI.

### 14. Contrato y versionado

- Se mantiene `/api/catalog/**`, `/api/inventory/**`, `/api/orders/**` y se agregan `/api/auth/**`, `/api/queue/**`, `/api/recommendations/**`, `/api/notifications/**`, `/api/orders/access/**`, `/api/orders/reports/**`.
- OpenAPI con `bearerAuth` y descripción de roles por operación; `docs/api-contract.md` actualizado en el mismo PR.
- Errores RFC 7807 con `code` de negocio; nuevos códigos: `QUEUE_REQUIRED`, `QUEUE_TOKEN_INVALID`, `QUEUE_CLOSED`, `ACCESS_DENIED`, `TICKET_ALREADY_USED`, `REPORT_FORBIDDEN`.
- Los endpoints internos no se publican en el OpenAPI público.

### 15. Patrones del enunciado y decisiones transversales

| Patrón / decisión | Estado | Referencia |
|---|---|---|
| API Gateway | Usado: punto único, JWT y roles | Decisión 2, ADR-003 |
| Circuit Breaker | Usado en clientes salientes con Resilience4j | Decisión 8 |
| Saga | Usado: compra orquestada con compensaciones | Decisión 7, ADR-007/013 |
| BFF | No usado: el gateway y la UI cubren el alcance | ADR-014 |
| Service Discovery | No usado: DNS de Compose y URLs por entorno | ADR-015 |
| Gateway y `auth_db` | R2DBC o `boundedElastic`; nunca JDBC en el event loop | ADR-016 |
| Payload de `orden.pagada` | Snapshot del evento al comprar | ADR-017 |
| Sobre de eventos | Versionado, con fixture y test de contrato | ADR-018, `docs/eventos.md` |

## Risks / Trade-offs

- **El gateway bloquea el event loop al leer `auth_db`** → se usa R2DBC o `boundedElastic` solo en el login (ADR-016); el JWT no consulta la base por request.
- **`orden.pagada` sale incompleto de orders** → migración 6.1 con snapshot de tipo/artistas/ciudad y contrato en `docs/eventos.md`; validación de P3/P1/P2 (ADR-017).
- **JWT rompe el flujo de la Entrega 1** → se implementa primero en el gateway, se actualizan Postman y tests, y los servicios siguen leyendo los mismos headers. Se coordina en un solo PR de contrato.
- **Redis como almacén primario de la fila** → AOF y healthcheck; si se pierde el estado, la fila se cierra y se documenta la recuperación. No se promete continuidad perfecta.
- **Tokens de fila reutilizables dentro de 5 min** → el límite por cliente y la no doble venta siguen vigentes; se documenta como simplificación.
- **Doble escritura en outbox** → se usa `FOR UPDATE SKIP LOCKED` y publicador idempotente; los consumidores deduplican por `eventId`.
- **Complejidad de la saga** → se limita a los pasos actuales y se prueba con fallos inyectados; no se agregan pasos nuevos.
- **MongoDB sin transacciones multi-documento** → las escrituras de historial son upserts idempotentes; no se requiere una transacción entre colecciones.
- **Loki/Promtail consumen recursos** → perfil de observabilidad y retención corta; se puede levantar sin ese perfil para pruebas de carga.
- **La prueba de carga puede fallar por la máquina** → se corre con el compose en un equipo con recursos y se documenta el hardware; se reportan percentiles y errores, no solo un número.
- **Tiempo del equipo** → se prioriza P0 (JWT, fila, acceso, notificaciones, reportes, Docker, no doble venta) y se recortan P1 (recomendaciones avanzadas, cache de búsqueda, Grafana) antes que los requisitos críticos.
- **Datos de prueba inconsistentes** → seeds idempotentes y verificación cruzada en el E2E; los snapshots se generan desde catalog.

## Migration Plan

1. **Contrato y fundaciones (Día 1):** actualizar `docs/api-contract.md`, matriz de roles, esquema de eventos y variables de entorno. Agregar Redis, MongoDB y RabbitMQ al compose de desarrollo.
2. **Seguridad (Días 1-2):** crear `auth_db`/`usuario`, login y filtro JWT en el gateway; actualizar Postman y tests. Mantener compatibilidad temporal con headers solo en tests internos.
3. **Servicios nuevos (Días 2-5):** esqueleto y Dockerfile de queue, recommendation y notification; migraciones y modelos; contratos internos.
4. **Catalog e inventory (Días 2-5):** alta demanda, edición, outbox, cache, datos de reporte, token de fila y cancelación de reserva. Migraciones Flyway nuevas.
5. **Orders (Días 3-6):** acceso QR, reportes, email, saga, outbox y resiliencia. Migraciones Flyway nuevas.
6. **Integración (Días 6-7):** eventos, consumidores, DLQ, reconciliación, observabilidad y compose integral; smoke test.
7. **Verificación (Días 8-9):** integración entre servicios, seguridad, carga, documentación y checklist.
8. **Cierre (Día 10):** clone limpio, tag `entrega-2`, validación de OpenSpec y entrega.

Rollback de desarrollo: `docker compose down -v` y volver a levantar; no hay datos productivos. Para un servicio, se puede volver a la imagen anterior porque las migraciones son aditivas; las columnas nuevas tienen defaults o son nullable. El cambio de identidad no tiene rollback transparente: si se vuelve atrás, se restaura el filtro de headers y se actualiza el contrato.

## Open Questions

Ninguna que cambie el alcance, los specs o la lista de tareas. Quedan como decisiones de implementación: tamaño de tanda y TTL de fila (variables de entorno), umbrales exactos de circuit breaker y cantidad de usuarios de la prueba de carga; se fijan al configurar cada tarea y se documentan en el README.
