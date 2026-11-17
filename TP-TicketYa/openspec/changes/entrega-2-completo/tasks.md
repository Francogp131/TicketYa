# Tasks

> **Reparto:** P1 IvÃ¡n (catalog + notification), P2 Pablo (inventory + queue), P3 Caro (orders), P4 Tiago (gateway + infraestructura), P5 Franco (recommendation + calidad e integraciÃ³n). Cada tarea indica su dueÃ±o y la verificaciÃ³n que debe quedar en el PR. La carga estimada por persona estÃ¡ en `docs/plan-entrega-2.md`.

## 1. Fundaciones, contrato y entorno

- [ ] 1.1 (P5) Verificar el acceso al repositorio del curso `Pabloaguirre23/ArquitecturaWeb`, la carpeta `TP-TicketYa/`, los cinco colaboradores con Write y la protecciÃ³n de `main`; actualizar la plantilla de PR para referenciar `entrega-2-completo`. Verificar con un clone limpio, un push de prueba en una rama descartable y un PR de prueba. _(Acceso verificado: 5 colaboradores con push y plantilla actualizada. `main` no tiene branch protection; requiere que Pablo la active como admin.)_
- [x] 1.2 (P5) Completar en `docs/features-y-user-stories.md` los criterios de aceptaciÃ³n de US-25 a US-38 y la matriz de trazabilidad US â†’ capability â†’ tarea; verificar que cada US de la Entrega 2 tenga al menos un escenario en `specs/` y una tarea asociada.
- [ ] 1.3 (P4) Congelar el contrato v2 en `docs/api-contract.md`: endpoints nuevos, JWT, roles, headers `X-User-*`, `X-Queue-Token`, `X-Internal-Token`, `X-Correlation-Id`, cÃ³digos de error y ejemplos; actualizar el esquema `bearerAuth` del OpenAPI. Verificar que no quede ningÃºn endpoint sin rol asignado y que P1/P2/P3/P5 lo revisen.
- [ ] 1.4 (P4) Agregar Redis 7, MongoDB 7 y RabbitMQ 3.13 a `docker-compose.dev.yml` (o a un compose de infraestructura separado) con healthchecks, volÃºmenes y variables en `.env.example`; verificar `docker compose config` y que los tres servicios queden saludables.
- [ ] 1.5 (P4) Definir el sobre de eventos (`eventId`, `type`, `occurredAt`, `correlationId`, `version`, `payload`), las routing keys y las colas/DLQ de RabbitMQ en `docs/eventos.md`; verificar con un mensaje de ejemplo y la revisiÃ³n de los consumidores. _Borrador completo en `docs/eventos.md` con fixture `docs/ejemplos/orden-pagada.json` y test de contrato del consumidor; falta que P4 lo congele y que P1/P3 validen sus productores._
- [ ] 1.6 (P2) Crear `queue-service` como proyecto Maven independiente con Spring Boot 3.5, Java 21, Actuator, springdoc, Redis y Dockerfile; verificar `mvn -q -DskipTests package`, `/actuator/health` y la conexiÃ³n a Redis.
- [x] 1.7 (P5) Crear `recommendation-service` como proyecto Maven independiente con Spring Boot 3.5, Java 21, Actuator, springdoc, Spring Data MongoDB y Dockerfile; verificar build, health y conexiÃ³n a MongoDB. _Hecho el DÃ­a 2: `mvn test` verde, `/actuator/health` UP con MongoDB y OpenAPI exportado._
- [x] 1.8 (P1) Crear `notification-service` como proyecto Maven independiente con Spring Boot 3.5, Java 21, Actuator, springdoc, Spring AMQP, PostgreSQL y Dockerfile; verificar build, health y conexiÃ³n a la base y al broker. _Hecho: `mvn test` verde el DÃ­a 3 con Testcontainers PostgreSQL/RabbitMQ; el health reporta `db` y `rabbit` UP._

## 2. Seguridad JWT y autorizaciÃ³n

- [ ] 2.1 (P4) Crear la base `auth_db`, la tabla `usuario` con email Ãºnico y hash BCrypt, los usuarios de prueba por rol y `POST /api/auth/login`; verificar 200 con token, 401 con credenciales invÃ¡lidas y 400 con datos incompletos.
- [ ] 2.2 (P4) Reemplazar el filtro de identidad por uno de Spring Security + JWT: validar firma y expiraciÃ³n, aplicar la matriz de roles, borrar `X-User-*` externos y propagar `X-User-Id`, `X-User-Role`, `X-User-Email` y correlaciÃ³n; verificar 401 sin token, 403 con rol incorrecto, 200 con rol correcto y descarte de headers falsificados.
- [ ] 2.3 (P4) Proteger `/internal/**` con `X-Internal-Token`, impedir su acceso desde el gateway y propagar el token y la correlaciÃ³n en todos los clientes REST; verificar 401 sin token interno, 404/403 por gateway y llamadas internas exitosas.
- [x] 2.4 (P1) Adaptar catalog-service y notification-service a la identidad JWT y a la matriz de roles (organizador dueÃ±o, admin, cliente y personal de acceso); verificar tests de 401/403/200 en los endpoints propios y del servicio de notificaciones.
- [ ] 2.5 (P2) Adaptar inventory-service y queue-service a la identidad JWT y roles; verificar que solo el cliente reserva y consulta su reserva, que la fila es propia y que los endpoints internos exigen token.
- [ ] 2.6 (P3) Adaptar orders-service a la identidad JWT y roles, incluyendo `ACCESS_STAFF` para el escaneo y `ORGANIZER`/`ADMIN` para reportes; verificar 401/403/200 y que un cliente no acceda a reportes ni a entradas ajenas.
- [ ] 2.7 (P5) Adaptar recommendation-service a la identidad JWT: preferencias y recomendaciones solo del cliente autenticado; verificar 401 sin token, 403 al intentar datos de otro y 200 con el dueÃ±o.
- [ ] 2.8 (P4) Crear la matriz de seguridad endpoint Ã— rol en `docs/seguridad.md` y una carpeta Postman/Newman que recorra todos los endpoints con cada rol; verificar que cada fila tenga evidencia de 401, 403 o 200 segÃºn corresponda.

## 3. catalog-service: alta demanda, ediciÃ³n, outbox y cache

- [x] 3.1 (P1) MigraciÃ³n Flyway nueva para `evento.alta_demanda`, lÃ­mite por cliente configurable y ediciÃ³n de evento publicado; implementar `PUT /api/catalog/events/{id}` con validaciones de dueÃ±o, fecha y estado. Verificar tests de ediciÃ³n vÃ¡lida, 403, 409 y 400, y que el snapshot de inventario se actualice. _(Hecho el DÃ­a 1: migraciÃ³n V5, ediciÃ³n parcial, snapshot versionado y tests verdes. Cambio de lugar publicado queda 409 hasta que inventory soporte reemplazo de sectores/asientos; reducciÃ³n de lÃ­mite con ventas queda pendiente de contrato con orders/inventory.)_
- [x] 3.2 (P1) Crear `outbox_event` y el publicador de eventos de catÃ¡logo (`evento.publicado`, `evento.actualizado`, `evento.cancelado`) con reintentos e idempotencia; verificar que el evento se escriba en la misma transacciÃ³n y que un fallo de RabbitMQ no pierda el cambio.
- [x] 3.3 (P1) Configurar Redis Cache en catalog-service para lugares, detalle estÃ¡tico y bÃºsqueda, con TTL configurables, claves por parÃ¡metros, `sync=true` y cabecera `X-Cache`; verificar hit, miss, expiraciÃ³n y que la disponibilidad y los asientos nunca se cacheen.
- [x] 3.4 (P1) Invalidar cache al publicar, editar, cancelar o cambiar precios; verificar que el detalle y las bÃºsquedas afectadas se recalculen y que un Redis caÃ­do degrade a la base sin romper la consulta.
- [x] 3.5 (P1) Exponer endpoints internos de datos de reporte (`organizador`, sectores, capacidad, precios) y candidatos de recomendaciÃ³n (ciudad, tipo, artista, fechas) con `X-Internal-Token`; verificar 200 con token, 401 sin token y que solo devuelvan eventos publicados/agotados.
- [ ] 3.6 (P1) Integrar catalog-service con queue-service al publicar, cancelar y agotar un evento de alta demanda, con timeout, reintentos y fallback; verificar que la fila se abra/cierre y que un queue-service caÃ­do no impida publicar un evento normal.
- [ ] 3.7 (P1) Actualizar OpenAPI, README del servicio y `docs/api-contract.md` con alta demanda, ediciÃ³n, cache, lÃ­mite y endpoints internos; verificar Swagger UI, ejemplos y cÃ³digos de error.

## 4. queue-service: fila virtual

- [ ] 4.1 (P2) Implementar el modelo Redis de la fila: sorted set por evento, hash de estado, ingreso idempotente con `ZADD NX`, posiciÃ³n (`ZRANK + 1`) y consulta de estado; verificar tests de primer ingreso, reingreso sin duplicar, posiciÃ³n y 409 en evento sin fila.
- [ ] 4.2 (P2) Implementar el scheduler de tandas con tamaÃ±o e intervalo configurables, habilitaciÃ³n FIFO, emisiÃ³n de token JWT (`eventId`, `sub`, `jti`, `exp`, `type=QUEUE`) y limpieza de vencidos; verificar que no se habilite dos veces al mismo cliente y que el token venza.
- [ ] 4.3 (P2) Cerrar la fila al cancelar, finalizar o agotar el evento, invalidar tokens nuevos y definir la recuperaciÃ³n ante reinicio de Redis (AOF o cierre controlado); verificar con tests y con un reinicio del contenedor.
- [ ] 4.4 (P2) Actualizar OpenAPI, README y contrato de queue-service; documentar la simplificaciÃ³n de polling/tandas y verificar que el flujo completo se pueda ejecutar con `curl` y Postman.

## 5. inventory-service: token de fila, cancelaciÃ³n y resiliencia

- [ ] 5.1 (P2) MigraciÃ³n Flyway nueva para `alta_demanda` y `queue_jti`; validar el token de fila en la reserva (firma, expiraciÃ³n, evento, cliente) y exigirlo solo en eventos de alta demanda. Verificar 403 sin token/vencido/ajeno y reserva normal con token vÃ¡lido.
- [ ] 5.2 (P2) Implementar `DELETE /api/inventory/reservations/{id}` para cancelar una reserva propia, liberar asientos/cupo y responder de forma idempotente; verificar 204/200, repeticiÃ³n sin doble liberaciÃ³n, 403 ajeno y 409/410 segÃºn estado.
- [ ] 5.3 (P2) Agregar timeouts, reintentos y circuit breaker a las llamadas de inventory hacia catalog (callbacks y reconciliaciÃ³n), con fallback y logs de correlaciÃ³n; verificar con WireMock los casos 5xx, timeout y recuperaciÃ³n.
- [ ] 5.4 (P2) Reconciliar disponibilidad y estado del evento con queue-service y catalog ante fallos parciales; verificar que una reserva confirmada descuente una sola vez, que una liberaciÃ³n repetida no aumente el cupo y que la prueba de no doble venta de 100 hilos siga verde. _Plan de evidencia multi-instancia en `docs/pruebas.md` Â§10: dos instancias contra la misma `inventory_db`._
- [ ] 5.5 (P2) Actualizar OpenAPI, README y contrato de inventory-service; verificar Swagger UI, ejemplos de 403/409/410 y la integraciÃ³n con el flujo de fila.

## 6. orders-service: saga, outbox, acceso y reportes

- [ ] 6.1 (P3) MigraciÃ³n Flyway nueva para `cliente_email`, `evento_organizador_id`, snapshot de sector, `evento_tipo`/`evento_artistas`/`evento_ciudad` para `orden.pagada` (ADR-017), `entrada.used_at/used_by`, `saga_compra`, `outbox_event`, `audit_log` e Ã­ndices de reporte; verificar que las migraciones apliquen sobre una base de Entrega 1 sin pÃ©rdida de datos.
- [ ] 6.2 (P3) Implementar la saga de compra como mÃ¡quina de estados con pasos idempotentes (`RESERVA_VALIDADA`, `ORDEN_CREADA`, `PAGO_APROBADO`, `INVENTARIO_CONFIRMADO`, `ENTRADAS_EMITIDAS`) y compensaciones; verificar pago aprobado, rechazado, timeout, fallo de inventario y fallo de emisiÃ³n, y que nunca quede `PAGADA` sin entradas.
- [ ] 6.3 (P3) Implementar el outbox y publicar `orden.pagada`, `orden.reintegrada` y `devolucion.aprobada` en la misma transacciÃ³n del cambio de estado; verificar que un fallo de RabbitMQ no pierda el evento y que un reintento no duplique el efecto. _Payload y sobre a respetar: `docs/eventos.md`; incluir `eventoTipo`, `artistas` y `ciudad` del snapshot de la orden._
- [ ] 6.4 (P3) Implementar `POST /api/orders/access/validate` con `ACCESS_STAFF`/`ADMIN`, actualizaciÃ³n condicional atÃ³mica, distinciÃ³n 404/409 y bitÃ¡cora; verificar dos escaneos concurrentes del mismo QR (uno 200, otro 409) y entrada de otro evento.
- [ ] 6.5 (P3) Implementar reportes por evento y sector con ventas, devoluciones, ocupaciÃ³n y recaudaciÃ³n, autorizaciÃ³n por organizador dueÃ±o/admin y datos de capacidad/precio desde catalog con cache y fallback; verificar 200, 403, 404 y cÃ¡lculos con devoluciones.
- [ ] 6.6 (P3) Agregar timeouts, reintentos, circuit breaker y fallback a la pasarela de pagos y a inventory, con claves de idempotencia; verificar con WireMock los casos lento, 5xx, circuito abierto y recuperaciÃ³n, y que no se cobre dos veces.
- [ ] 6.7 (P3) Actualizar OpenAPI, README y contrato de orders-service con acceso, reportes, saga y eventos; verificar Swagger UI, ejemplos y la matriz de roles de los endpoints nuevos.

## 7. recommendation-service: MongoDB, preferencias y recomendaciones

- [x] 7.1 (P5) Configurar MongoDB, colecciones `preferencias`, `historial_compras` y `eventos_vistos`, Ã­ndices y datos de prueba; verificar que los Ã­ndices existan y que un cliente tenga como mÃ¡ximo un documento de preferencias. _Hecho el DÃ­a 2: Ã­ndices creados por `MongoIndexInitializer`, seed idempotente y test de Testcontainers verde; evidencia en `docs/evidencia/entrega-2-dia-2.txt`._
- [x] 7.2 (P5) Implementar `PUT`/`GET /api/recommendations/preferences` con validaciÃ³n de tipos, artistas y ciudad, upsert idempotente y acceso solo al propio cliente; verificar 200, 400, 403 y actualizaciÃ³n sin duplicados. _Hecho el DÃ­a 2: 200/400/401/403 probados con MockMvc y curl, y upsert sin duplicados contra MongoDB real._
- [x] 7.3 (P5) Consumir `orden.pagada` y registrar el historial de forma idempotente por `eventId`; verificar que un evento duplicado no duplique el historial y que una compra reintegrada no se registre. _Hecho el DÃ­a 3: listener RabbitMQ, upsert con Ã­ndice Ãºnico y test de Testcontainers con duplicado y reintegro._
- [x] 7.4 (P5) Implementar recomendaciones por preferencias y co-compra, con motivos, exclusiÃ³n de eventos no elegibles, fallback de populares y resiliencia ante catalog; verificar cliente con historial, cliente nuevo, sin candidatos y catalog caÃ­do. _Hecho el DÃ­a 3: ranking explicable, co-compra en dos consultas, fallback a `eventos_vistos` y tests unitarios/HTTP._
- [ ] 7.5 (P5) Implementar `GET /api/recommendations/events/{eventId}/similar` y actualizar OpenAPI, README y contrato; verificar resultados ordenados por frecuencia y lista vacÃ­a cuando no hay datos.

## 8. notification-service: eventos, reintentos y consulta

- [x] 8.1 (P1) Crear la tabla `notificacion`, la interfaz `NotificationSender` y el sender mock de email/push; verificar que un envÃ­o exitoso quede `ENVIADA` y que un fallo quede `PENDIENTE` con intentos y prÃ³ximo reintento.
- [x] 8.2 (P1) Consumir `orden.pagada` y generar la notificaciÃ³n de compra de forma idempotente, con reintentos y DLQ; verificar que la compra no se bloquee si el consumidor falla y que un evento duplicado no duplique el aviso.
- [x] 8.3 (P1) Consumir `evento.cancelado` y `evento.actualizado` para notificar a todos los compradores, y `evento.publicado` para notificar a clientes con artista favorito en su ciudad consultando preferencias; verificar coincidencia, no coincidencia y evento sin compradores. _(Compradores desde la proyección local alimentada por `orden.pagada`; la búsqueda de preferencias usa `GET /internal/preferences?artista=&ciudad=` documentado en `docs/api-contract.md` §6.3.1 y queda pendiente de implementación en recommendation-service. Tests unitarios verdes con stub y degradación a lista vacía si no responde.)_
- [x] 8.4 (P1) Implementar `GET /api/notifications` con solo las notificaciones propias, estados, intentos y paginación; actualizar OpenAPI, README y contrato y verificar 200, 403 y orden por fecha. _(Incluye `GET /api/notifications/{id}` para responder `403 NOTIFICATION_FORBIDDEN` ante una notificación ajena, como pide el spec. Tests: `ConsultaNotificacionServiceTest` y `NotificacionControllerTest`.)_

## 9. Observabilidad, Docker y CI

- [ ] 9.1 (P4) Configurar logging JSON con Logstash Encoder y MDC en los siete servicios, con filtros que registren mÃ©todo, ruta, usuario, rol, estado, duraciÃ³n y correlaciÃ³n; verificar que una solicitud deje la misma correlaciÃ³n en gateway y servicio.
- [ ] 9.2 (P1) Crear `audit_log` en catalog-service y registrar publicaciÃ³n, ediciÃ³n y cancelaciÃ³n de eventos con usuario, resultado, detalle y correlaciÃ³n; verificar una operaciÃ³n exitosa y una rechazada.
- [ ] 9.3 (P3) Crear `audit_log` en orders-service y registrar compra, devoluciÃ³n, cancelaciÃ³n e ingreso QR; verificar que la bitÃ¡cora y el log compartan la correlaciÃ³n de una compra completa.
- [ ] 9.4 (P4) Agregar Loki, Promtail y Grafana al compose con datasource provisionado, retenciÃ³n de 7 dÃ­as y consulta por `X-Correlation-Id`; verificar una bÃºsqueda real por correlaciÃ³n en Grafana.
- [ ] 9.5 (P4) Crear un Dockerfile multi-stage por servicio, con usuario no root, healthcheck y variables de entorno; verificar `docker build` de los siete servicios y el arranque individual.
- [ ] 9.6 (P4) Crear `docker-compose.yml` integral con gateway, siete servicios, cinco bases, Redis, MongoDB, RabbitMQ y observabilidad, con redes, volÃºmenes, healthchecks y `depends_on`; verificar `docker compose config`, `up --build` y el smoke test.
- [ ] 9.7 (P4) Crear/actualizar el CI en la raÃ­z del repo del curso (`.github/workflows/`, con `paths: TP-TicketYa/**`; GitHub no ejecuta el workflow que vive dentro de `TP-TicketYa/.github/`) para construir y testear los siete servicios, validar `docker compose config`, correr la suite de integraciÃ³n con Docker y verificar OpenAPI; verificar el workflow verde en un PR de prueba. _Workflow raÃ­z agregado en este PR; se activa al mergear a `main`._

## 10. IntegraciÃ³n, carga, documentaciÃ³n y cierre

- [ ] 10.1 (P5) Crear el mÃ³dulo `integration-tests` con Testcontainers/Docker Compose y cinco flujos: login â†’ bÃºsqueda, fila â†’ reserva, compra â†’ QR, acceso QR y reporte, y compra â†’ recomendaciÃ³n/notificaciÃ³n; verificar la suite verde contra el compose real.
- [ ] 10.2 (P3) Actualizar la colecciÃ³n Postman/Newman v2 con auth, fila, compra, acceso, reportes, preferencias, recomendaciones, notificaciones y la carpeta de seguridad; verificar una corrida completa 100 % verde por el gateway.
- [ ] 10.3 (P2) Crear el plan JMeter del flujo de compra (login, bÃºsqueda, reserva, pago) con datos de prueba y corridas de 500 y 1000 usuarios concurrentes; guardar los resultados crudos y el comando reproducible.
- [ ] 10.4 (P5) Escribir `docs/informe-carga.md` con hardware, usuarios, throughput, p50/p95/p99, errores y anÃ¡lisis de cuello de botella; verificar que las conclusiones se desprendan de los resultados guardados.
- [ ] 10.5 (P3) Actualizar `docs/arquitectura.md` con el diagrama de mÃ³dulos ampliado, la secuencia de compra con saga/outbox, la fila virtual, el acceso QR y las notificaciones; verificar que los Mermaid rendericen y coincidan con `design.md`.
- [ ] 10.6 (P5) Actualizar `docs/modelo-datos.md` (tablas y colecciones nuevas), `docs/decisiones.md` (ADR-014 en adelante), `docs/api-contract.md` y los README de los servicios nuevos; verificar enlaces y coherencia con las migraciones.
- [ ] 10.7 (P5) Actualizar `docs/pruebas.md` con la estrategia y la evidencia de integraciÃ³n, seguridad, concurrencia y carga; completar `docs/checklist-entrega-2.md` al 100 % y actualizar `docs/README.md` como Ã­ndice; verificar que cada Ã­tem del enunciado tenga evidencia.
- [ ] 10.8 (P4) Probar un clone limpio, `docker compose up --build`, el smoke test y el flujo E2E; crear el tag `entrega-2` sobre el commit entregado y verificar el acceso al repositorio del curso.
- [ ] 10.9 (P5) Ejecutar `openspec validate entrega-2-completo --strict`, revisar que todas las tareas estÃ©n marcadas y que no queden TODOs ni secretos; dejar el resumen de cierre y el enlace de entrega.

## Workflow follow-up

- Archivar el change con `openspec archive entrega-2-completo` una vez entregada y revisada la Entrega 2.
- Sincronizar los specs principales y verificar con `openspec validate --all --strict`.
