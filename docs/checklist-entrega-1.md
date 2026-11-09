# Checklist de Entrega 1 — TicketYa

Lista verificable de **todo** lo que pide el enunciado para la Entrega 1. Cada fila tiene una tarea de OpenSpec y una evidencia concreta. Se completa al final de cada día y se revisa completa el 9 y 10 de noviembre.

**Leyenda:** `[ ]` pendiente · `[x]` verificado con evidencia.

**Estado 9/11:** checklist en revisión por observaciones externas. No entregar hasta cerrar
los P0 de cancelación/ventas, callback de disponibilidad, devolución parcial, sector cruzado,
cupo de zona y reintegros. El detalle está en `tasks.md` (2.4) y en el seguimiento del equipo.

## 0. Estado de verificación (Día 9 — 9/11/2026, en revisión)

| Área | Estado | Evidencia |
|---|---|---|
| CI de `main` | Verde en el merge del PR #18 (`08ba5c5`); el PR de P5 repite la suite | GitHub Actions |
| RC de catálogo (6.4) | Clone limpio del PR #18: suite catalog 54/0/3 y build de los cuatro servicios | [`evidencia/dia-7-rc-catalog.txt`](evidencia/dia-7-rc-catalog.txt) |
| Suite completa (6.1) | catalog 54, inventory 30, orders 46, gateway 5; 135 tests, 0 fallos, 0 omitidos con Docker | [`pruebas.md`](pruebas.md), [`evidencia/dia-6-suite.txt`](evidencia/dia-6-suite.txt) |
| Suite catalog sin Docker (6.1) | 54 tests, 0 fallos, 3 de integración omitidos | [`evidencia/dia-6-catalog-suite.txt`](evidencia/dia-6-catalog-suite.txt) |
| E2E por gateway (6.2) | 45/45 requests y 45/45 assertions; corrida real de los seis flujos el Día 6 | [`evidencia/dia-6-newman.txt`](evidencia/dia-6-newman.txt) |
| ER por servicio (5.5) | catalog, inventory y orders validados contra Flyway | [`modelo-datos.md`](modelo-datos.md) |
| Índice y ADR (5.9) | Índice actualizado y 11 decisiones registradas | [`README.md`](README.md), [`decisiones.md`](decisiones.md) |
| Arquitectura (5.7) | Límites, consistencia y compensaciones enlazados a `design.md` | [`arquitectura.md`](arquitectura.md) |
| README raíz (5.6) | Clone limpio y build de los cuatro proyectos; CI valida el compose | `README.md`, run `e9ae4cb` |
| OpenAPI agregado (5.8) | Los tres archivos exportados y revisados contra el contrato | `openapi/*.json` |
| Postman | 45 requests con assertions; Newman 45/45 (Día 6) | `postman/TicketYa.postman_collection.json` |
| RC (6.4) | Tag `entrega-1-rc` y clone limpio del candidato | [`evidencia/dia-7-rc.txt`](evidencia/dia-7-rc.txt) |
| Discrepancia de contrato (no bloqueante) | `DELETE /api/inventory/reservations/{reservaId}` documentado en 4.5 pero no implementado; reportado a P2 | [`pruebas.md` §7.1](pruebas.md#71-discrepancia-de-contrato-detectada-en-el-control-del-día-7) |
| Cierre (6.4) | Tag final `entrega-1` sobre `5874fdf`, repo público y 5 colaboradores con Write | [`evidencia/dia-8-entrega.txt`](evidencia/dia-8-entrega.txt) |
| Revisión externa (9/11) | En corrección: cancelación/ventas, callback de disponibilidad, devolución parcial, sector cruzado, cupo de zona y reintegros | [`tasks.md`](../openspec/changes/entrega-1-core/tasks.md) |

## 1. Funcionalidad de la Entrega 1

| # | Requisito del enunciado | User stories | Spec | Tareas | Evidencia |
|---|---|---|---|---|---|
| 1 | Eventos con ID, nombre, descripción, tipo, organizador, participantes, fecha/hora, lugar y estado | US-04, US-08 | `gestion-eventos-lugares` | 2.2, 2.3 | `EventServiceTest` (22) + `Crear evento`, `Publicar evento` y `Detalle del evento publicado` en Postman |
| 2 | Lugares con nombre, dirección, lat/long y sectores numerados o no numerados; precio por sector | US-01, US-02, US-05 | `gestion-eventos-lugares` | 2.1, 2.2 | `VenueServiceTest` (3), `VenueControllerTest` (6) + `Crear lugar`, `Crear sector numerado/no numerado` y precios en Postman |
| 3 | Borrador → configuración → publicación; admin puede suspender/cancelar | US-06, US-07 | `gestion-eventos-lugares` | 2.2, 2.3, 2.4 | `EventServiceTest`, `InventoryRestClientTest` (4), `OrdersRestClientTest` (3) + `Publicar evento` y `Cancelar evento (ADMIN)` en Postman |
| 4 | Reserva temporal (10 min), pago, QR único, detalle de compra y medio de pago, límite por cliente | US-12 a US-19 | `reserva-y-compra` | 3.2 a 3.6, 4.2 a 4.5 | E2E de compra aprobada + tests de límite y vencimiento |
| 5 | Una entrada nunca se vende dos veces; reintento de pago idempotente | US-20, US-21 | `reserva-y-compra` | 3.7, 4.6 | Test de 100 hilos + test de `Idempotency-Key` |
| 7 | Devolución hasta 72 h antes, aprobación/rechazo del admin, liberación y reintegro; cancelación reintegra todo (la notificación a compradores es Entrega 2) | US-22, US-23, US-24 | `devoluciones-cancelacion` | 4.8, 4.9 | Tests de devolución y cancelación + Postman |
| 9 | Búsqueda por tipo, organizador, participante, ciudad y rango de fechas; orden por fecha, precio o relevancia | US-09, US-10, US-11 | `busqueda-eventos` | 2.5 | `EventServiceTest`, `EventoBusquedaIntegrationTest` (3, CI) + `Buscar eventos por ciudad y orden` y error 400 en Postman |

> Los ítems 6, 8, 10, 11 y 12 del enunciado son de Entrega 2 y están fuera de alcance (ver sección 5).

## 2. Trazabilidad de user stories (US-01 a US-24)

| US | Historia | Spec | Tarea | Verificación |
|---|---|---|---|---|
| US-01 | Crear lugar | `gestion-eventos-lugares` | 2.1 | 201, 400 por coordenadas, 403 |
| US-02 | Definir sectores | `gestion-eventos-lugares` | 2.1 | Asientos generados, 400 por capacidad |
| US-03 | Consultar lugares | `gestion-eventos-lugares` | 2.1 | Listado y 404 |
| US-04 | Crear evento en borrador | `gestion-eventos-lugares` | 2.2 | `BORRADOR`, 400 fecha/tipo |
| US-05 | Precios por sector | `gestion-eventos-lugares` | 2.2 | Precio guardado, 400 sector ajeno, 409 publicado |
| US-06 | Publicar evento | `gestion-eventos-lugares` | 2.2, 2.4 | `PUBLICADO` + inventario, 409 incompleto, 403 ajeno |
| US-07 | Suspender/cancelar | `gestion-eventos-lugares` | 2.3, 4.9 | `CANCELADO` + reintegro, 409 finalizado, 403 |
| US-08 | Estados del evento | `gestion-eventos-lugares` | 2.3 | `AGOTADO`, `PUBLICADO`, `FINALIZADO`, 409 |
| US-09 | Buscar multi-criterio | `busqueda-eventos` | 2.5 | Filtros combinados, vacío, 400 rango |
| US-10 | Ordenar y paginar | `busqueda-eventos` | 2.5 | Orden fecha/precio/relevancia, total, 400 |
| US-11 | Detalle del evento | `busqueda-eventos` | 2.5 | 200 con sectores/precios, 404 borrador |
| US-12 | Reservar asientos | `reserva-y-compra` | 3.2 | 10 min, 409, todo o nada |
| US-13 | Reservar zona | `reserva-y-compra` | 3.3 | Descuenta N, 409 sin disponibilidad |
| US-14 | Límite por cliente | `reserva-y-compra` | 3.4 | 422 al superar, éxito dentro |
| US-15 | Vencimiento y liberación | `reserva-y-compra` | 3.5, 4.5 | Libera, 410 al pagar vencida |
| US-16 | Iniciar compra | `reserva-y-compra` | 4.2 | `PENDIENTE_PAGO`, total, 404/403 |
| US-17 | Pago aprobado | `reserva-y-compra` | 4.4 | `PAGADA`, vendidos, QR único |
| US-18 | Pago rechazado | `reserva-y-compra` | 4.5, 4.7 | `RECHAZADA`, libera, compensa |
| US-19 | Ver mis entradas | `reserva-y-compra` | 4.10 | Solo propias, 403 ajenas |
| US-20 | No doble venta | `reserva-y-compra` | 3.7 | 100 hilos: 1 éxito, 99 conflictos |
| US-21 | Idempotencia | `reserva-y-compra` | 4.6 | Misma respuesta, 422 cuerpo distinto |
| US-22 | Solicitar devolución | `devoluciones-cancelacion` | 4.8 | `PENDIENTE`, 422 fuera de plazo, 409 usada |
| US-23 | Resolver devolución | `devoluciones-cancelacion` | 4.8 | Libera + invalida + refund; rechazo mantiene válida |
| US-24 | Cancelación de evento | `devoluciones-cancelacion` | 4.9 | Todo `EN_REINTEGRO` + refund |

## 3. Arquitectura y requisitos no funcionales de la Entrega 1

- [x] **Microservicios Spring Boot independientes:** cada proyecto compila y se ejecuta por separado (`mvn package` en las cuatro carpetas). → 1.2
- [x] **Punto de entrada único:** el gateway expone `/api/catalog`, `/api/inventory` y `/api/orders`; los servicios no se consumen directo. → 1.6
- [x] **Comunicación REST mayormente síncrona:** publicación, reserva, compra, devolución y cancelación documentadas en el contrato. → 1.3, 2.4
- [x] **Una base relacional por microservicio:** `catalog_db`, `inventory_db`, `orders_db` con Flyway. → 1.5
- [x] **Mecanismo contra la doble venta probado y multi-instancia:** locks/updates condicionales en PostgreSQL + test de concurrencia. → 3.7, `design.md` decisión 6
- [x] **Pasarela de pagos simulada:** aprobado, rechazado y lento; interfaz reemplazable. → 4.3
- [x] **Sin autenticación en Entrega 1:** identidad por `X-User-Id` y `X-User-Role` propagados por el gateway; 401/403 según corresponda. → 1.6
- [x] **Ejecución local:** README con Docker para las bases y Maven/IDE para los servicios; clone limpio y build verificados. → 5.6
- [x] **Consistencia y compensaciones:** flujo de compra con estados intermedios y reintentos idempotentes documentado; la estrategia completa de Saga/outbox queda para Entrega 2. → `design.md` decisión 7, 4.7

## 4. Tareas y entregables de la Entrega 1 (enunciado)

- [x] **Features y user stories de todo el sistema con criterios de aceptación:** `docs/features-y-user-stories.md` cubre las 12 funcionalidades y US-01 a US-38. → 1.8
- [x] **Definición de microservicios, responsabilidades y API pública:** `design.md` + `docs/api-contract.md` + límites en `docs/arquitectura.md`. → 1.3
- [x] **Diagrama de módulos de la arquitectura:** Mermaid en `docs/arquitectura.md`. → 5.3
- [x] **Diagrama de secuencia de compra de entrada numerada (aprobado, rechazado y vencido):** Mermaid en `docs/arquitectura.md`. → 5.4
- [x] **ER por microservicio con claves, relaciones y entidades solapadas:** `docs/modelo-datos.md`. → 5.5
- [x] **Listado de endpoints con método, ruta, parámetros, códigos y ejemplos JSON:** `docs/api-contract.md` + OpenAPI por servicio. → 1.3
- [x] **OpenAPI/Swagger con descripciones, ejemplos y códigos de error:** Swagger UI de los tres servicios + `openapi.json`. → 2.6, 3.8, 4.11, 5.8
- [x] **Implementación completa de la funcionalidad de la entrega:** grupos 2, 3 y 4 de `tasks.md`. → 2.1 a 4.11
- [x] **README con instrucciones de ejecución y documentación técnica adicional:** README raíz + `docs/arquitectura.md`. → 5.6, 5.7
- [x] **Índice de documentación y registro de decisiones:** `docs/README.md` y `docs/decisiones.md` (ADR-001 a ADR-011). → 5.9
- [x] **README por microservicio:** responsabilidad, endpoints, modelo y comandos de test en los cuatro servicios. → 5.10
- [x] **Evidencia de pruebas:** estrategia, comandos y resultados de concurrencia/E2E en `docs/pruebas.md`. → 5.11
- [x] **Pruebas unitarias de compra, devoluciones y búsqueda:** → 2.5, 4.4, 4.5, 4.7, 4.8, 4.9
- [x] **Prueba de compras simultáneas del mismo asiento o último lugar de zona:** → 3.7
- [x] **Repositorio con código, especificaciones, documentación de diseño, README, datos de prueba y colección Postman:** todo versionado en `main`; el tag y el enlace final se cierran en 6.4. → 5.1, 5.2, 6.3, 6.4

## 5. Fuera de alcance (Entrega 2)

No se exige en esta entrega; no debe consumir tiempo del cronograma:

- Fila virtual (ítem 6) y control de acceso con QR (ítem 8).
- Preferencias y recomendaciones (ítem 10) y MongoDB.
- Notificaciones (ítem 11) y reportes (ítem 12).
- JWT con roles, cache, resiliencia avanzada, log centralizado y comunicación asíncrona.
- Docker por microservicio y `docker-compose.yml` de todo el sistema.
- Pruebas de integración entre servicios, pruebas de seguridad y prueba de carga.

## 6. Puerta de salida (no se entrega si falla)

- [x] El repositorio es accesible y el tag `entrega-1` apunta al commit entregado. _(Tag `entrega-1` sobre `5874fdf`; repo público y clone limpio del tag verificados en [`evidencia/dia-8-entrega.txt`](evidencia/dia-8-entrega.txt))_
- [x] `main` tiene el CI verde y la suite `mvn test` pasa en los cuatro proyectos (135 tests, 0 omitidos). Evidencia: [`evidencia/dia-6-suite.txt`](evidencia/dia-6-suite.txt).
- [x] Un clone limpio sigue el README y levanta el sistema (3 bases healthy, 4 health 200, gateway 200/401). Evidencia: [`evidencia/dia-5-clone-limpio.txt`](evidencia/dia-5-clone-limpio.txt) y [`evidencia/dia-7-rc.txt`](evidencia/dia-7-rc.txt).
- [x] La prueba de no doble venta pasa de forma reproducible (`ConcurrenciaPostgresTest`: 100 hilos, 1 ganador).
- [x] Los seis flujos E2E de Postman pasan por el gateway: corrida real 45/45 requests y 45/45 assertions del Día 6 en [`evidencia/dia-6-newman.txt`](evidencia/dia-6-newman.txt).
- [x] `docs/README.md` no tiene enlaces rotos y los endpoints de los flujos de la Entrega 1 están en el contrato y en el OpenAPI (26 paths / 30 operaciones, incluidos los internos). Queda una discrepancia no bloqueante: `DELETE /api/inventory/reservations/{reservaId}` figura en el contrato 4.5 pero no está implementado ni en el OpenAPI; ver [`pruebas.md` §7.1](pruebas.md#71-discrepancia-de-contrato-detectada-en-el-control-del-día-7).
- [x] Cada ítem verificado de este checklist tiene evidencia enlazada en el PR o en `docs/`.
- [ ] No quedan tareas P0 abiertas en `openspec/changes/entrega-1-core/tasks.md`. _(2.4 y las observaciones del 9/11 están en corrección)_
