# Checklist de Entrega 2 — TicketYa

Lista verificable de todo lo que pide el enunciado para la Entrega 2. Cada fila tiene una
tarea de OpenSpec y una evidencia concreta. Se completa al final de cada día y se revisa
completa el 20 y 21 de noviembre.

**Leyenda:** `[ ]` pendiente · `[x]` verificado con evidencia.

**Estado inicial (13/11):** planificación completa. El change `entrega-2-completo` tiene
propuesta, 15 specs, diseño y 64 tareas, y valida con `openspec validate --strict`. La
implementación arranca el Día 1.

## 0. Estado de verificación

| Área | Estado | Evidencia |
|---|---|---|
| Change OpenSpec | Planificado y validado | `openspec validate entrega-2-completo --strict` |
| Criterios US-25 a US-38 | Completos y trazados | `docs/features-y-user-stories.md` (tarea 1.2) |
| recommendation-service | Build, tests, health y Docker OK | `recommendation-service/` (tareas 1.7, 7.1–7.4) |
| notification-service (esqueleto) | Build, health y Testcontainers OK | `notification-service/` (tarea 1.8) |
| Acceso al repo del curso | 5 colaboradores con push; branch protection pendiente | Tarea 1.1 |
| Sobre de eventos y payloads | Borrador con fixture y test de contrato | [`eventos.md`](eventos.md) (1.5, pendiente de congelar) |
| ADR de la Entrega 2 | ADR-014 a ADR-018 | [`decisiones.md`](decisiones.md) |
| Contrato v2 y matriz de roles | Pendiente | `docs/api-contract.md` |
| JWT y autorización | Pendiente | Tareas 2.1–2.8 |
| Fila virtual | Pendiente | Tareas 4.1–4.4, 5.1 |
| Control de acceso QR | Pendiente | Tarea 6.4 |
| Preferencias y recomendaciones | En curso (7.1–7.4) | `recommendation-service` + tests Mongo/Rabbit |
| Notificaciones | Pendiente | Tareas 8.1–8.4 |
| Reportes | Pendiente | Tarea 6.5 |
| Consistencia, saga y outbox | Pendiente | Tareas 6.2, 6.3, 3.2 |
| Resiliencia | Pendiente | Tareas 5.3, 6.6 |
| Cache | Pendiente | Tareas 3.3, 3.4 |
| Observabilidad y log central | Pendiente | Tareas 9.1–9.4 |
| Docker y compose | Pendiente | Tareas 9.5–9.7 |
| Integración, seguridad y carga | Pendiente | Tareas 10.1–10.4 |
| Cierre y tag | Pendiente | Tareas 10.8–10.9 |

## 1. Funcionalidad de la Entrega 2

| # | Requisito del enunciado | User stories | Spec | Tareas | Evidencia |
|---|---|---|---|---|---|
| 6 | Fila virtual para eventos de alta demanda: posición, habilitación por tandas y token temporal | US-25, US-26, US-27 | `fila-virtual` | 4.1–4.4, 5.1 | Tests de queue e inventory + Postman |
| 8 | Control de acceso: validar QR, evento correcto, uso único y concurrencia | US-28 | `control-acceso-qr` | 6.4 | Test de dos escaneos concurrentes + Postman |
| 10 | Preferencias (tipos, artistas, ciudad) y recomendaciones por historial y co-compra | US-29, US-30 | `preferencias-recomendaciones` | 7.1–7.5 | Tests de Mongo/Rabbit + Postman |
| 11 | Notificaciones de compra, cancelación/cambio y artista favorito, sin bloquear la compra | US-31, US-32, US-33 | `notificaciones` | 8.1–8.4 | Tests de consumidor y DLQ + evidencia de compra con broker caído |
| 12 | Reportes por evento: vendidas y devueltas por sector, ocupación y recaudación | US-34 | `reportes-organizador` | 6.5 | Test de cálculo + Postman |
| — | Los ítems 1–5, 7 y 9 de la Entrega 1 siguen funcionando con JWT | US-01 a US-24 | specs de Entrega 1 | 2.1–2.8, 10.1 | Suite de regresión + E2E |

## 2. Arquitectura y requisitos no funcionales

- [ ] **Microservicios Spring Boot independientes:** siete servicios y el gateway, cada uno con su `pom.xml`, build y ejecución separada. → 9.5, 9.6
- [ ] **Punto de entrada único:** todo el tráfico externo entra por el gateway; las bases y brokers no están publicados. → 9.6
- [ ] **Comunicación asíncrona justificada:** RabbitMQ para notificaciones, historial de recomendaciones y eventos de catálogo; documentación de por qué. → 1.5, 6.3, 8.2. _(Contrato y fixture en [`eventos.md`](eventos.md); falta congelar y publicar desde los productores.)_
- [x] **Base NoSQL:** MongoDB para preferencias e historial, con justificación y consultas. → 7.1. Evidencia: [`recommendation-service/README.md`](../recommendation-service/README.md#modelo-mongodb), índices verificados en `RecommendationMongoIntegrationTest` y [`evidencia/entrega-2-dia-2.txt`](evidencia/entrega-2-dia-2.txt).
- [ ] **Consistencia entre servicios y compensaciones:** saga de compra, outbox, idempotencia y política de consistencia documentada. → 6.2, 6.3, 3.2
- [ ] **Resiliencia:** timeouts, reintentos, circuit breaker y fallback en pasarela, inventory, catalog, queue y notification. → 5.3, 6.6, 2.3
- [ ] **Cache:** datos cacheados, TTL, invalidación y datos que no se cachean (disponibilidad, asientos, reservas). → 3.3, 3.4
- [ ] **Trazabilidad:** log de cada solicitud con endpoint, usuario, estado y duración; correlación entre servicios. → 9.1
- [ ] **Bitácora:** operaciones críticas de compra, devolución, cancelación, publicación e ingreso. → 9.2, 9.3
- [ ] **Log centralizado:** JSON a stdout + Loki/Promtail/Grafana consultable por correlación. → 9.4
- [ ] **Seguridad:** JWT con roles `CLIENT`, `ORGANIZER`, `ADMIN` y `ACCESS_STAFF`; matriz endpoint × rol; endpoints internos protegidos. → 2.1–2.8
- [ ] **Despliegue:** un contenedor por servicio y un `docker-compose.yml` único con bases, Redis, MongoDB, RabbitMQ y observabilidad. → 9.5, 9.6
- [ ] **Rendimiento:** prueba de carga del flujo de compra con informe de usuarios, tiempos y errores. → 10.3, 10.4

## 3. Features y user stories

- [x] **Criterios de aceptación de US-25 a US-38 completos** y actualizados respecto del enunciado. → 1.2
- [ ] **Criterios de US-01 a US-24 actualizados** por JWT, fila virtual, notificaciones y reportes. → 1.2, 2.4–2.7
- [x] **Matriz US → capability → tarea → evidencia** sin historias huérfanas. → 1.2

## 4. Arquitectura y modelo de datos

- [ ] **Diagrama de módulos actualizado** con gateway, siete servicios, bases, Redis, MongoDB, RabbitMQ y observabilidad. → 10.5
- [ ] **Diagrama de secuencia de compra** con saga, outbox, compensaciones y notificaciones. → 10.5
- [ ] **Diagrama de secuencia de fila virtual** (ingreso, tandas, token, reserva). → 10.5
- [ ] **Diagrama de secuencia de acceso QR** con concurrencia. → 10.5
- [ ] **ER por microservicio actualizado** con tablas nuevas, claves, relaciones y entidades solapadas. → 10.6
- [ ] **Modelo MongoDB** con colecciones, documentos e índices. → 10.6
- [ ] **Estrategia de consistencia** (fuerte vs. eventual) y manejo de fallos parciales documentado. → 6.2, 6.3, 10.5
- [ ] **Decisiones tecnológicas justificadas:** MongoDB, Redis, RabbitMQ, JWT, Resilience4j, outbox, saga, cache y observabilidad. → 10.6. _(ADR-014 a ADR-018 cubren BFF, Service Discovery, gateway/`auth_db`, payload de `orden.pagada` y sobre de eventos.)_

## 5. Diseño de API REST

- [ ] **Contrato v2** con método, ruta, parámetros, roles, códigos y ejemplos JSON. → 1.3
- [ ] **OpenAPI por servicio** con `bearerAuth`, descripciones, ejemplos y códigos de error. → 2.4–2.7, 3.7, 5.5, 6.7, 7.5, 8.4
- [ ] **Endpoints internos** documentados aparte y no expuestos en el OpenAPI público. → 1.3, 2.3
- [ ] **Colección Postman/Newman v2** con auth, fila, compra, acceso, reportes, recomendaciones, notificaciones y seguridad. → 10.2

## 6. Implementación

- [ ] **JWT, roles y matriz de acceso** en gateway y servicios. → 2.1–2.8
- [ ] **Fila virtual** con Redis, posición, tandas y token. → 4.1–4.4
- [ ] **Token de fila** exigido por inventory en eventos de alta demanda. → 5.1
- [ ] **Control de acceso QR** con uso único y concurrencia. → 6.4
- [ ] **Preferencias y recomendaciones** con MongoDB, historial y co-compra. → 7.1–7.5 _(7.1–7.4 hechos; queda 7.5)_
- [ ] **Notificaciones** asíncronas con reintentos, DLQ y consulta propia. → 8.1–8.4
- [ ] **Reportes** por evento y sector con autorización. → 6.5
- [ ] **Saga, outbox y compensaciones** en la compra. → 6.2, 6.3
- [ ] **Resiliencia** en todas las dependencias externas. → 5.3, 6.6, 2.3
- [ ] **Cache** de catálogo con TTL e invalidación. → 3.3, 3.4
- [ ] **Observabilidad** con logs JSON, correlación, bitácora y Loki. → 9.1–9.4
- [ ] **Docker** por servicio y compose integral. → 9.5, 9.6
- [ ] **Datos de prueba** para usuarios, eventos de alta demanda, preferencias e historial. → 1.8, 7.1, 10.1

## 7. Testing

- [ ] **Unitarios** de la lógica nueva (fila, acceso, reportes, recomendaciones, notificaciones, saga, cache). → tareas de cada servicio
- [ ] **No doble venta** con 100 hilos y última unidad de zona, sigue verde con la fila y el token. → 5.4, 10.1
- [ ] **Integración entre servicios** con Testcontainers/Compose: login, fila, compra, QR, reporte, recomendación y notificación. → 10.1
- [ ] **Seguridad de endpoints:** cada endpoint solo accesible por sus roles; 401 sin token y 403 con rol incorrecto. → 2.8
- [ ] **Idempotencia de eventos:** duplicados no duplican historial ni notificaciones. → 7.3, 8.2
- [ ] **Resiliencia:** timeout, 5xx, circuito abierto y recuperación con WireMock. → 5.3, 6.6
- [ ] **Prueba de carga** de 500 y 1000 usuarios con resultados crudos y análisis. → 10.3, 10.4

## 8. Documentación y entregables

- [ ] **README raíz** con requisitos, `docker compose up --build`, variables, usuarios de prueba, URLs y comandos. → 10.6, 10.8
- [ ] **README por servicio** actualizado con endpoints, modelo, variables y tests. → 3.7, 5.5, 6.7, 7.5, 8.4
- [ ] **`docs/api-contract.md`** actualizado. → 1.3, 10.6
- [ ] **`docs/arquitectura.md`** actualizado. → 10.5
- [ ] **`docs/modelo-datos.md`** actualizado. → 10.6
- [ ] **`docs/seguridad.md`** con la matriz endpoint × rol. → 2.8
- [ ] **`docs/pruebas.md`** con estrategia y evidencia. → 10.7
- [ ] **`docs/informe-carga.md`** con hardware, usuarios, throughput, percentiles y errores. → 10.4
- [ ] **`docs/decisiones.md`** con las ADR nuevas. → 10.6
- [ ] **`docs/README.md`** como índice actualizado. → 10.7
- [ ] **`docs/checklist-entrega-2.md`** al 100 % con evidencia. → 10.7
- [ ] **OpenAPI exportado** de cada servicio en `openapi/`. → tareas de cada servicio
- [ ] **Colección Postman** actualizada. → 10.2

## 9. Cierre

- [ ] **Suite completa** de los siete servicios verde, sin tests omitidos. → 10.8
- [ ] **Clone limpio** con `docker compose up --build` y smoke test. → 10.8
- [ ] **Tag `entrega-2`** sobre el commit entregado. → 10.8
- [ ] **Repositorio del curso accesible** y enlace de entrega verificado. → 10.8, 10.9
- [ ] **Change validado** con `openspec validate entrega-2-completo --strict`. → 10.9
- [ ] **Change archivado** después de la entrega. → Workflow follow-up
