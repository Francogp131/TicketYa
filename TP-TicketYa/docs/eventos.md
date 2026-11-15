# Eventos de dominio y RabbitMQ

Contrato del sobre de eventos y de los payloads que comparten los servicios. Es la base de
la tarea **1.5** y debe quedar congelado antes de que los productores (3.2, 6.3) y los
consumidores (7.3, 8.2) avancen por separado.

- **Estado:** borrador para validar por P4 (dueño de 1.5), P1, P3 y P5. Los consumidores ya
  implementados usan este esquema; cualquier cambio se discute acá y se actualiza el fixture
  [`ejemplos/orden-pagada.json`](ejemplos/orden-pagada.json).
- **Broker:** RabbitMQ 3.13, exchange `ticketya.events` de tipo `topic`.
- **Entrega:** al menos una vez. Los consumidores deduplican por `eventId`.

## 1. Sobre

```json
{
  "eventId": "e0000000-0000-4000-8000-000000000001",
  "type": "orden.pagada",
  "occurredAt": "2026-11-15T12:00:00Z",
  "correlationId": "5d1f2e3a-4b6c-4d8e-9f01-2a3b4c5d6e7f",
  "version": 1,
  "payload": {}
}
```

| Campo | Tipo | Obligatorio | Regla |
|---|---|---|---|
| `eventId` | UUID | sí | Identificador único del evento de dominio. Es la clave de idempotencia del consumidor y se reutiliza en los reintentos del outbox. |
| `type` | string | sí | Routing key y tipo del payload. |
| `occurredAt` | ISO-8601 UTC | sí | Momento en que ocurrió el hecho de negocio, no el de publicación. |
| `correlationId` | UUID/texto | sí | Permite seguir la operación entre servicios y logs. |
| `version` | integer | sí | Versión del schema del payload. Arranca en `1`. |
| `payload` | object | sí | Datos del evento; su forma depende de `type`. |

Reglas de compatibilidad:

- Solo cambios **aditivos**: agregar campos opcionales no rompe consumidores.
- Los consumidores ignoran campos desconocidos (`FAIL_ON_UNKNOWN_PROPERTIES=false`).
- Quitar o cambiar el significado de un campo requiere un `type` nuevo o `version` nueva.
- `eventId` no se reutiliza para un hecho distinto; un reintento del outbox conserva el mismo.

## 2. Topología

Exchange: `ticketya.events` (topic, durable). Dead-letter exchange: `ticketya.events.dlx`
(direct, durable). Cada cola tiene su DLQ `*.dlq` y `default-requeue-rejected=false`.

| Routing key | Cola | Consumidor | DLQ |
|---|---|---|---|
| `orden.pagada` | `notification.order` | notification-service (8.2) | `notification.order.dlq` |
| `orden.pagada` | `recommendation.history` | recommendation-service (7.3) | `recommendation.history.dlq` |
| `orden.reintegrada` | `notification.order` | notification-service (8.2) | `notification.order.dlq` |
| `devolucion.aprobada` | `notification.order` | notification-service (8.2) | `notification.order.dlq` |
| `evento.actualizado` | `notification.event` | notification-service (8.3) | `notification.event.dlq` |
| `evento.cancelado` | `notification.event` | notification-service (8.3) | `notification.event.dlq` |
| `evento.publicado` | `notification.favorite` | notification-service (8.3) | `notification.favorite.dlq` |
| `entrada.utilizada` | — | sin consumidor en esta entrega | — |

Un mismo evento puede tener más de una cola; cada consumidor mantiene su propia idempotencia.

## 3. Payloads

### 3.1 `orden.pagada`

Se publica **después** de que la saga confirmó inventory y emitió las entradas. Si la compra
se compensa, se publica `orden.reintegrada` en lugar de `orden.pagada`.

```json
{
  "ordenId": "f0000000-0000-4000-8000-000000000001",
  "clienteId": "33333333-3333-4333-8333-333333333333",
  "eventoId": "d0000000-0000-4000-8000-000000000001",
  "eventoNombre": "Festival de Rock Tandil 2027",
  "eventoTipo": "FESTIVAL",
  "artistas": ["Los Piojos", "Callejeros"],
  "ciudad": "Tandil",
  "fechaEvento": "2027-03-15T00:00:00Z",
  "estado": "PAGADA"
}
```

| Campo | Obligatorio | Uso |
|---|---|---|
| `ordenId` | sí | Referencia de la orden y clave de idempotencia de negocio. |
| `clienteId` | sí | Dueño del historial y destinatario de la notificación. |
| `eventoId` | sí | Evento comprado; se excluye de recomendaciones futuras. |
| `eventoNombre` | recomendado | Texto de la notificación y motivo de recomendación. |
| `eventoTipo` | sí | Ranking por tipo favorito. |
| `artistas` | sí | Ranking por artista favorito; puede ser `[]`. |
| `ciudad` | sí | Ranking por ciudad y fallback de populares. |
| `fechaEvento` | sí | Filtro de eventos futuros y orden. |
| `estado` | sí | Debe ser `PAGADA`; otro valor no se registra en el historial. |

**Origen de los datos:** orders ya guarda `eventoNombre` y `eventoFecha` como snapshot. Para
`eventoTipo`, `artistas` y `ciudad` la decisión es **snapshot al comprar** (ADR-017): catalog
los incluye en el snapshot de publicación hacia inventory, inventory los devuelve en la
reserva y orders los persiste en `orden` (migración 6.1). Si el equipo prefiere el atajo,
orders puede consultar el endpoint interno de catalog (3.5) al crear la orden y guardar el
mismo snapshot; lo que no se acepta es que recommendation los busque después, porque el
historial quedaría incompleto si catalog está caído.

### 3.2 `orden.reintegrada`

```json
{
  "ordenId": "f0000000-0000-4000-8000-000000000001",
  "clienteId": "33333333-3333-4333-8333-333333333333",
  "eventoId": "d0000000-0000-4000-8000-000000000001",
  "monto": 25000.00,
  "motivo": "PAGO_RECHAZADO"
}
```

Lo consume notification-service para avisar el reintegro. recommendation-service **no** está
suscripto: el historial solo registra compras pagadas.

### 3.3 `evento.publicado` / `evento.actualizado` / `evento.cancelado`

```json
{
  "eventoId": "d0000000-0000-4000-8000-000000000001",
  "nombre": "Festival de Rock Tandil 2027",
  "tipo": "FESTIVAL",
  "artistas": ["Los Piojos", "Callejeros"],
  "ciudad": "Tandil",
  "fechaEvento": "2027-03-15T00:00:00Z",
  "organizadorId": "22222222-2222-4222-8222-222222222222",
  "estado": "PUBLICADO"
}
```

- `evento.publicado`: notification-service busca clientes con el artista favorito en la
  ciudad y les avisa (8.3).
- `evento.actualizado` y `evento.cancelado`: notification-service avisa a los compradores.
- recommendation-service puede actualizar su snapshot `eventos_vistos` en una tarea futura;
  no es obligatorio para 7.3/7.4.

### 3.4 `devolucion.aprobada`

```json
{
  "devolucionId": "9a1b...",
  "ordenId": "f0000000-0000-4000-8000-000000000001",
  "entradaId": "7c2d...",
  "clienteId": "33333333-3333-4333-8333-333333333333",
  "monto": 25000.00
}
```

Lo consume notification-service para confirmar el reintegro al cliente.

### 3.5 `entrada.utilizada`

```json
{
  "entradaId": "7c2d...",
  "eventoId": "d0000000-0000-4000-8000-000000000001",
  "validadoPor": "55555555-5555-4555-8555-555555555555",
  "utilizadaEn": "2027-03-14T23:10:00Z"
}
```

Se reserva para la bitácora/observabilidad de la tarea 9.3. No tiene consumidor en esta
entrega.

## 4. Idempotencia y reintentos

- **Productor:** escribe el evento en `outbox_event` en la misma transacción del cambio de
  negocio. El publicador toma pendientes con `FOR UPDATE SKIP LOCKED`, publica y marca
  `PUBLICADO`; si RabbitMQ falla, reintenta con backoff y conserva el mismo `eventId`.
- **Consumidor:** guarda o aplica el efecto una sola vez por `eventId` (o por la clave única
  de negocio). En recommendation la clave es `(eventId, clienteId, eventoId)`; en
  notification será `eventId` + tipo de aviso.
- **Errores:** los mensajes inválidos o las excepciones no se reencolan infinitamente; van a
  la DLQ de la cola. La DLQ se revisa manualmente y se reintenta solo después de corregir la
  causa.
- **Orden:** no se garantiza orden entre eventos. Cada payload debe ser autosuficiente.

## 5. Test de contrato

- Fixture compartido: [`ejemplos/orden-pagada.json`](ejemplos/orden-pagada.json).
- `recommendation-service` tiene un test que lee ese fixture y lo deserializa con el mismo
  `OrdenPagadaEvent` del listener (`OrdenPagadaEventContractTest`). Si el contrato cambia, el
  test falla y obliga a actualizar el fixture y el consumidor en el mismo PR.
- Productores y notification-service deben agregar su test equivalente al implementar 3.2,
  6.3 y 8.2.
- El test de integración con RabbitMQ (`RecommendationRabbitIntegrationTest`) valida el
  consumo real, el duplicado y el reintegro.

## 6. Pendientes y responsables

| Pendiente | Dueño | Tarea |
|---|---|---|
| Congelar este documento y agregar el test de contrato del productor | P4 | 1.5 |
| Publicar `orden.pagada` desde el outbox de orders con el payload de §3.1 | P3 | 6.3 |
| Publicar `evento.*` desde catalog | P1 | 3.2 |
| Consumir `orden.pagada` para notificaciones | P1 | 8.2 |
| Validar el snapshot `eventoTipo`/`artistas`/`ciudad` en orders | P3 + P1/P2 | 6.1 / 3.5 |
| Confirmar que el broker y las colas se declaran en el compose integral | P4 | 9.6 |
