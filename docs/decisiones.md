# Decisiones de arquitectura (ADR) — Entrega 1

Registro corto de las decisiones que sostienen la Entrega 1. Cada ADR indica la decisión,
las alternativas que se descartaron y las consecuencias que aceptamos. El desarrollo técnico
completo está en [`../openspec/changes/entrega-1-core/design.md`](../openspec/changes/entrega-1-core/design.md)
y los diagramas en [`arquitectura.md`](arquitectura.md).

## ADR-001 — Microservicios por dominio con base propia

- **Decisión:** `catalog-service`, `inventory-service`, `orders-service` y `api-gateway`, cada uno con su base PostgreSQL y sin claves foráneas entre bases. La integración es REST por `/internal/**`.
- **Alternativas:** monolito modular (más simple, pero no cumple el requisito de microservicios); que catalog sea dueño de la disponibilidad (mezcla lectura de catálogo con escritura transaccional de asientos).
- **Consecuencias:** cada servicio se construye, testea y despliega por separado. Se acepta consistencia eventual y se resuelve con snapshots e idempotencia, no con transacciones distribuidas.

## ADR-002 — PostgreSQL 16 + Flyway, sin H2

- **Decisión:** PostgreSQL 16 para runtime y para los tests de integración/concurrencia con Testcontainers. Flyway versiona esquema y seeds.
- **Alternativas:** H2 en tests (descartado: su semántica de locks no representa a PostgreSQL); MySQL (válido, pero PostgreSQL ofrece `FOR UPDATE`, `RETURNING` y `ON CONFLICT`).
- **Consecuencias:** la prueba de no doble venta corre contra el motor real. Requiere Docker en local y en CI; los tests de Testcontainers se omiten si Docker no está disponible.

## ADR-003 — Gateway único e identidad por headers

- **Decisión:** todo el tráfico público entra por `api-gateway` (`/api/catalog/**`, `/api/inventory/**`, `/api/orders/**`). El gateway exige y propaga `X-User-Id` y `X-User-Role`, y agrega `X-Correlation-Id`. Los `/internal/**` no se exponen.
- **Alternativas:** validar identidad en cada servicio (duplica lógica); implementar JWT en la Entrega 1 (fuera de alcance).
- **Consecuencias:** los servicios quedan simples y en la Entrega 2 solo cambia el gateway. Un header ausente o un rol inválido se resuelve con RFC 7807 (401/403) antes de llegar al servicio.

## ADR-004 — Contrato REST congelado y errores RFC 7807

- **Decisión:** [`api-contract.md`](api-contract.md) es la vista humana y el OpenAPI de cada servicio la fuente machine-readable. Todos los errores usan `application/problem+json` con `status`, `title`, `detail`, `instance`, `code` y `errors[]`.
- **Alternativas:** documentación por servicio sin contrato central; errores ad hoc por endpoint.
- **Consecuencias:** cualquier cambio de contrato se discute y actualiza en el mismo PR. Postman, los tests de controller y el gateway dependen de un formato estable.

## ADR-005 — Snapshot de publicación de catalog hacia inventory

- **Decisión:** al publicar, catalog envía a inventory un snapshot completo (evento, fecha, límite, sectores, precios y asientos) con clave idempotente `eventoId + version`. Inventory guarda el snapshot y no consulta catalog en el camino crítico de la reserva.
- **Alternativas:** que inventory consulte catalog en cada reserva (acopla la disponibilidad a la latencia y caída de catalog).
- **Consecuencias:** reservas rápidas y resistentes a fallas de catalog; el precio y la capacidad quedan congelados al publicar. Republicar es idempotente y no reinicia lo vendido.

## ADR-006 — La no doble venta la garantiza la base

- **Decisión:** asientos numerados con `SELECT ... FOR UPDATE` ordenado por ID dentro de una transacción; zonas con `UPDATE ... WHERE disponibles >= N RETURNING`; límite por cliente con `pg_advisory_xact_lock`; vencimiento y liberación con updates condicionales.
- **Alternativas:** locks en memoria o `synchronized` (no sirven con varias instancias); lock distribuido en Redis (infraestructura extra); optimistic locking (más reintentos).
- **Consecuencias:** con 100 hilos sobre el mismo asiento hay exactamente un ganador. Los deadlocks se mitigan tomando locks en orden y reintentando la transacción hasta 3 veces.

## ADR-007 — Orquestación de compra en orders con compensaciones

- **Decisión:** orders orquesta reserva → pago → confirmación en inventory → emisión de entradas. Si falla la confirmación o la emisión, deja la orden en `REINTEGRO_PENDIENTE` (nunca `PAGADA`), reintegra el pago y libera los lugares.
- **Alternativas:** transacción distribuida 2PC (sobredimensionada); coreografía con eventos (Entrega 2).
- **Consecuencias:** flujo síncrono simple y testeable, con estados intermedios trazables. La Saga/outbox formal queda como deuda para la Entrega 2.

## ADR-008 — Idempotencia de compra persistida

- **Decisión:** `POST /api/orders` exige `Idempotency-Key`. Orders guarda en `idempotency_key` el hash del cuerpo, el estado (`EN_PROCESO`/`COMPLETADA`) y la respuesta. Misma clave y mismo cuerpo devuelven la respuesta guardada; cuerpo distinto responde 422; clave en proceso responde 409 con `Retry-After`.
- **Alternativas:** deduplicar por `reservaId` (no cubre cambios de cuerpo); deduplicación en memoria (se pierde al reiniciar y no sirve multi-instancia).
- **Consecuencias:** los reintentos no generan otra orden ni otro cobro, incluso en concurrencia. La clave expira a las 24 h.

## ADR-009 — Pasarela de pagos mock detrás de una interfaz

- **Decisión:** `PaymentGateway` con una implementación mock que responde según el token (`APROBADO`, `RECHAZADO`, `LENTO`) y un `reintegrar` idempotente.
- **Alternativas:** integrar una pasarela real (fuera de alcance y con credenciales); lógica de pago embebida en `OrderService` (difícil de testear y reemplazar).
- **Consecuencias:** tests deterministas de aprobado, rechazado y timeout; en la Entrega 2 se cambia el adapter sin tocar la orquestación.

## ADR-010 — Devoluciones con ventana de 72 h y cancelación con reintento

- **Decisión:** la devolución se solicita hasta 72 h antes del evento, pasa por `PENDIENTE → PROCESANDO → APROBADA/RECHAZADA` y solo puede haber una abierta por entrada (índice único parcial). La cancelación de evento la inicia catalog y la ejecuta orders de forma idempotente; si orders falla, catalog guarda `cancelacion_pendiente` y un scheduler reintenta.
- **Alternativas:** que orders detecte la cancelación por polling (más lento y costoso); que catalog libere los lugares directamente (innecesario: un evento cancelado no se vuelve a vender).
- **Consecuencias:** una devolución aprobada libera el lugar, invalida la entrada y reintegra; un rechazo la deja `VALIDA`. La cancelación es eventualmente consistente y reintentable.

## ADR-011 — Documentación y verificación en el mismo PR

- **Decisión:** cada PR incluye código, tests, OpenAPI, README del servicio y la marca en `tasks.md`. `docs/README.md` es el índice y `docs/checklist-entrega-1.md` la lista verificable.
- **Alternativas:** documentar todo al final (el riesgo detectado en el plan: llegar al Día 9 con documentación atrasada).
- **Consecuencias:** los PRs son más grandes, pero el estado del proyecto siempre es verificable. OpenSpec (`entrega-1-core`) es la fuente de verdad del alcance.

## ADR-012 — “Suspender” en la Entrega 1 se resuelve como cancelar

- **Contexto:** el enunciado (ítem 3) y US-07 hablan de “suspender o cancelar”, pero el modelo
  de estados de la Entrega 1 no tiene `SUSPENDIDO`.
- **Decisión:** en la Entrega 1, “suspender” se implementa como **cancelación** (`CANCELADO`):
  el evento deja de venderse y las entradas vendidas pasan a reintegro. El estado `SUSPENDIDO`
  y la reactivación quedan para la Entrega 2.
- **Alternativas:** agregar `SUSPENDIDO` ahora (migración, transiciones, sincronización con
  inventory y orders) o no cubrir el caso (peor).
- **Consecuencias:** se cumple el bloqueo de ventas y el reintegro; no se puede reanudar un
  evento suspendido. La decisión se valida con la cátedra.

## ADR-013 — Consistencia de compra y deuda de saga/outbox

- **Contexto:** la compra involucra orders, inventory y la pasarela. La implementación de la
  Entrega 1 usa una transacción local con llamadas HTTP y compensaciones best-effort.
- **Decisión:** mantener la orquestación síncrona de la Entrega 1 y documentar la limitación:
  si la transacción queda rollback-only después de un fallo de emisión, la compensación puede
  no persistirse. La saga/outbox formal y la separación de transacciones se encaran en la
  Entrega 2.
- **Alternativas:** 2PC (sobredimensionado), coreografía con eventos (Entrega 2) o dejar el
  comportamiento implícito (peor).
- **Consecuencias:** el flujo feliz y los rechazos están cubiertos; los fallos extremos
  requieren un test de integración y seguimiento. No se debe declarar la consistencia como
  total hasta implementar la saga.
