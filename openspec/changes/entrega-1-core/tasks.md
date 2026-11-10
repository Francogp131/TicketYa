# Tasks

## 1. Fundaciones, contrato y gateway

- [x] 1.1 Configurar el repositorio `Francogp131/TicketYa` (público para lectura, escritura por colaboradores), `main` protegida, `.gitignore`, plantilla de PR y convención de ramas `feat/*`; verificar con un clone limpio y un PR de prueba. _(5 colaboradores con Write; branch protection con 1 aprobación y los 5 checks del CI; clone limpio verificado en `docs/evidencia/dia-5-clone-limpio.txt`)_
- [x] 1.2 Crear los cuatro proyectos Spring Boot independientes (`catalog-service`, `inventory-service`, `orders-service`, `api-gateway`) con Java 21, Maven y Actuator; verificar `mvn -q -DskipTests package` y `/actuator/health` en cada uno.
- [x] 1.3 Congelar el contrato REST v1 en `docs/api-contract.md` (método, ruta, parámetros, códigos y ejemplos JSON); verificar que cada US-01 a US-24 tenga endpoint y códigos definidos.
- [x] 1.4 Implementar el manejo global de errores RFC 7807 (`application/problem+json`) y las validaciones comunes; verificar con tests que 400/401/403/404/409 devuelvan el formato acordado. _(handlers en los tres servicios y filtro del gateway; tests de 400/401/403/404/409 en catalog/inventory/orders/gateway)_
- [x] 1.5 Crear `docker-compose.dev.yml` con una base PostgreSQL por servicio y migraciones Flyway; verificar que `docker compose up -d` levante las tres bases y que las tablas existan.
- [x] 1.6 Configurar el gateway con rutas `/api/catalog/**`, `/api/inventory/**` y `/api/orders/**`, y propagación de `X-User-Id`/`X-User-Role`; verificar con `curl` que llegue a los tres servicios.
- [x] 1.7 Configurar CI con GitHub Actions (build + tests por servicio) y validación de PR; verificar que el workflow quede verde en un PR de prueba. _(workflow verde en el PR #1: 4 builds + validación de docker compose)_
- [x] 1.8 Revisar `docs/features-y-user-stories.md` contra los 12 ítems del enunciado y las 38 user stories; verificar que cada US tenga criterios de aceptación y esté referenciada en `docs/checklist-entrega-1.md`. _(12 funcionalidades y US-01 a US-38 con criterios; las US-01 a US-24 de la Entrega 1 están trazadas en el checklist)_

## 2. catalog-service: lugares, eventos, publicación y búsqueda

- [x] 2.1 Implementar el modelo y los endpoints de lugares, sectores y asientos (US-01 a US-03); verificar con tests 201/400/403/404 y la generación de asientos de sectores numerados.
- [x] 2.2 Implementar el modelo y los endpoints de eventos, precios y publicación (US-04 a US-06); verificar con tests fecha pasada, tipo inválido, sector ajeno, evento de otro organizador y publicación incompleta.
- [x] 2.3 Implementar las transiciones de estado y el scheduler de finalización (US-07 y US-08); verificar con tests `AGOTADO`, `PUBLICADO`, `CANCELADO`, `FINALIZADO` y transición inválida 409.
- [x] 2.4 Integrar la publicación con inventory-service mediante un cliente REST idempotente con reintentos, y el callback de disponibilidad (`AGOTADO`/`PUBLICADO`); verificar con WireMock/Testcontainers y republicar el mismo evento sin duplicar inventario. _(Publicación idempotente y cliente con reintentos OK; callback implementado en inventory (`PATCH /internal/events/{id}/availability`) y verificado con los tests de inventory/catalog. Corrección del 9/11 mergeada en #27.)_
- [x] 2.5 Implementar la búsqueda multi-criterio con orden y paginación y el detalle del evento (US-09 a US-11); verificar con tests de filtros combinados, lista vacía, rango inválido, orden por fecha/precio/relevancia, total de páginas y borrador 404.
- [x] 2.6 Documentar el OpenAPI de catalog-service con descripciones, ejemplos y errores; verificar Swagger UI y que `openapi.json` incluya todos los endpoints y códigos. _(`openapi/catalog-openapi.json` exportado con springdoc; anotaciones `@Operation`/`@ApiResponse`, ejemplos en los DTOs, seguridad `X-User-Id`/`X-User-Role` y esquema `ProblemDetail` con ejemplos para todos los 4xx/5xx)_

## 3. inventory-service: reservas y no doble venta

- [x] 3.1 Implementar el modelo de inventario y reservas con Flyway y el endpoint interno de alta de inventario; verificar que dos llamadas con la misma clave no dupliquen filas.
- [x] 3.2 Implementar la reserva atómica de asientos numerados, todo o nada; verificar con tests de asiento libre, asiento ocupado y reserva múltiple parcialmente no disponible (409 sin cambios).
- [x] 3.3 Implementar la reserva atómica de zonas no numeradas; verificar con tests de disponibilidad suficiente (descuenta N) e insuficiente (409 sin cambios). _(ReservaServiceTest + ConcurrenciaPostgresTest; 30 tests verdes en inventory)_
- [x] 3.4 Implementar el límite de entradas por cliente contando vendidas y reservas vigentes; verificar con tests 422 al superar el límite y éxito dentro del límite. _(cuenta cliente_evento + reservas VIGENTE)_
- [x] 3.5 Implementar el vencimiento de reservas (scheduler + validación perezosa) y la liberación de lugares; verificar con tests de liberación y de pago sobre reserva vencida (410). _(scheduler + validación perezosa)_
- [x] 3.6 Implementar la confirmación de venta, la liberación por pago rechazado y la liberación por devolución; verificar que confirmar o liberar dos veces sea idempotente. _(confirm/release/returns + tabla devolucion_procesada)_
- [x] 3.7 Escribir la prueba de concurrencia con PostgreSQL real (Testcontainers): 100 hilos sobre el mismo asiento y varios sobre el último lugar de una zona; verificar que exactamente uno tenga éxito y la base registre una sola reserva/venta. _(ConcurrenciaPostgresTest: 100 hilos por asiento y 20 por el último lugar de zona)_
- [x] 3.8 Documentar el OpenAPI de inventory-service con ejemplos y errores; verificar Swagger UI y los códigos 409/410/422.

## 4. orders-service: compra, pago, entradas y devoluciones

- [x] 4.1 Implementar el modelo de órdenes, ítems, pagos, entradas, devoluciones e idempotencia con Flyway; verificar que las migraciones creen todas las tablas y constraints.
- [x] 4.2 Implementar la creación de orden desde una reserva vigente con total calculado en el servidor; verificar con tests 201, reserva inexistente 404, reserva ajena 403 y reserva vencida 410.
- [x] 4.3 Implementar la pasarela de pagos mock (aprobado, rechazado y lento) detrás de una interfaz reemplazable; verificar con tests cada resultado y el timeout. _(PaymentGateway + MockPaymentGateway + OrderServiceTest)_
- [x] 4.4 Implementar el pago aprobado: confirmar inventario, pasar la orden a `PAGADA`, registrar medio de pago y emitir una entrada con QR único por lugar; verificar con tests que haya una entrada por asiento y QR únicos. _(TicketServiceTest + E2E de compra aprobada)_
- [x] 4.5 Implementar el pago rechazado y la reserva vencida: liberar lugares, orden `RECHAZADA` y ninguna entrada emitida; verificar con tests de estado y disponibilidad. _(OrderServiceTest + E2E de rechazo)_
- [x] 4.6 Implementar la idempotencia con `Idempotency-Key` (misma respuesta, 422 con cuerpo distinto y una sola orden ante reintentos concurrentes); verificar con tests contra la tabla de idempotencia. _(IdempotencyServiceTest + OrderIdempotencyIntegrationTest)_
- [x] 4.7 Implementar la compensación cuando falla la confirmación de inventario o la emisión de entradas después del pago; verificar con tests que la orden no quede `PAGADA` y que se solicite el reintegro. _(compensación por confirmación y por emisión en OrderServiceTest)_
- [x] 4.8 Implementar las devoluciones (US-22 y US-23): solicitud, aprobación y rechazo; verificar con tests 422 fuera de plazo, 409 ya resuelta, 403 entrada ajena, liberación + invalidación + refund al aprobar y entrada válida al rechazar. _(DevolucionServiceTest + ReturnControllerTest + E2E de devolución)_
- [x] 4.9 Implementar la cancelación de evento (US-24): pasar todas las entradas vendidas a `EN_REINTEGRO` y solicitar el reintegro; verificar con tests de evento con y sin entradas y que el endpoint sea idempotente. _(OrderServiceTest + cancelación idempotente del E2E; pagos pasan a REINTEGRADO)_
- [x] 4.10 Implementar la consulta de entradas y órdenes propias (US-19); verificar con tests que el cliente solo vea las suyas y que una entrada ajena devuelva 403. _(OrderQueryServiceTest + TicketControllerTest + E2E de listar órdenes/entradas)_
- [x] 4.11 Documentar el OpenAPI de orders-service con ejemplos y errores; verificar Swagger UI y los códigos 201/403/404/409/410/422. _(`openapi/orders-openapi.json` exportado con descripciones y códigos; ejemplos de request en la colección Postman)_

## 5. Integración, datos y documentación

- [x] 5.1 Crear los datos de prueba con Flyway (lugares, eventos publicados, precios e inventario); verificar que se puedan consultar por API después de levantar el entorno. _(V2__seed.sql en catalog e inventory; verificado por gateway)_
- [x] 5.2 Crear la colección Postman con los flujos de compra aprobada, rechazada, vencida, devolución y cancelación, con assertions; verificar que se ejecute completa contra el entorno local. _(45 requests / 45 assertions verdes por gateway, incluidos devolución aprobada y rechazada)_
- [x] 5.3 Crear el diagrama de módulos en Mermaid; verificar que muestre gateway, los tres servicios, sus bases, el mock de pagos y las dependencias. _(docs/arquitectura.md)_
- [x] 5.4 Crear el diagrama de secuencia de compra de una entrada numerada, incluyendo pago aprobado, pago rechazado y reserva vencida; verificar que coincida con los estados y llamadas del diseño. _(docs/arquitectura.md)_
- [x] 5.5 Crear el ER por microservicio, con claves, relaciones y entidades solapadas entre bases; verificar que coincida con las migraciones Flyway. _(catalog validado por P1 contra V1–V4; inventory y orders revisados contra V1/V3 y V1 en `docs/modelo-datos.md`; los tests de esquema con Testcontainers corren en CI)_
- [x] 5.6 Escribir el README raíz con requisitos, cómo levantar bases y servicios, variables, URLs y datos de prueba; verificar siguiéndolo en un clone limpio. _(clone limpio: compose healthy, health 200 en los cuatro servicios y smoke por gateway; evidencia en `docs/evidencia/dia-5-clone-limpio.txt`)_
- [x] 5.7 Escribir `docs/arquitectura.md` con decisiones, límites de servicio, estrategia de consistencia y compensaciones, enlazando `design.md`, contrato y diagramas; verificar la revisión cruzada del grupo. _(límites, consistencia y compensaciones + enlaces a design, contrato, ER y ADR)_
- [x] 5.8 Revisar el OpenAPI agregado de los tres servicios (descripciones, ejemplos y códigos de error); verificar que Swagger UI de cada servicio muestre todos los endpoints del contrato. _(tres archivos en `openapi/`; catalog reforzado con ProblemDetail y orders regenerado en 4.11)_
- [x] 5.9 Crear `docs/README.md` como índice de documentación y `docs/decisiones.md` con las ADR cortas (decisión, alternativas y consecuencias); verificar que todos los enlaces resuelvan y que el índice refleje el estado real. _(índice actualizado y ADR-001 a ADR-011 en `docs/decisiones.md`; enlaces verificados)_
- [x] 5.10 Documentar cada servicio en su `README.md` (responsabilidad, endpoints, modelo, variables y comandos de test); verificar que otra persona pueda levantarlo siguiendo solo ese README. _(catalog, inventory, orders y gateway con endpoints, modelo, variables y comandos)_
- [x] 5.11 Crear `docs/pruebas.md` con la estrategia, los comandos y la evidencia de la prueba de concurrencia y del E2E; verificar que los comandos documentados se ejecuten tal cual. _(suite 135 tests del Día 6/7 y Newman 45/45 del Día 6; evidencia en `docs/evidencia/dia-6-suite.txt` y `docs/evidencia/dia-6-newman.txt`)_

## 6. Verificación y entrega

- [x] 6.1 Ejecutar la suite completa (`mvn test`) de los cuatro proyectos y el CI sobre `main`; verificar todo verde y sin tests omitidos. _(catalog 54, inventory 30, orders 46, gateway 5: 135 tests, 0 fallos, 0 omitidos con Java 21 y Docker; CI verde en el merge del PR #18 `08ba5c5`; evidencia en `docs/evidencia/dia-6-suite.txt`)_
- [x] 6.2 Ejecutar el E2E por el gateway de los seis flujos críticos (publicar, buscar, reservar, comprar, devolver y cancelar); verificar con Postman/Newman y dejar la salida como evidencia. _(corrida real del Día 6: 45/45 requests y 45/45 assertions por gateway sobre `08ba5c5`; salida completa en `docs/evidencia/dia-6-newman.txt`)_
- [x] 6.3 Completar `docs/checklist-entrega-1.md` al 100 %; verificar que no quede ningún ítem sin evidencia. _(todos los ítems verificables con evidencia enlazada; el tag final `entrega-1` se cerró en 6.4)_
- [x] 6.4 Probar un clone limpio, crear el tag `entrega-1`, verificar el acceso al repositorio y entregar el enlace; verificar que el tag apunte al commit entregado. _(Día 8: tag `entrega-1` sobre `5874fdf` (merge del PR #19), repo público con 5 colaboradores Write y clone limpio del tag verificado en `docs/evidencia/dia-8-entrega.txt`. Los commits posteriores al tag solo agregan pruebas o documentación de cierre.)_

## Workflow follow-up

- Archivar el change con `openspec archive entrega-1-core` una vez entregada y revisada la Entrega 1.
- Sincronizar los specs principales y verificar con `openspec validate --all`.
