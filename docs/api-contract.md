# Contrato REST v1 — TicketYa Entrega 1

Este documento es la **vista humana** del contrato. La fuente machine-readable es el OpenAPI de cada servicio (`/v3/api-docs` y Swagger UI en `/swagger-ui.html`). Los dos se actualizan en el mismo PR.

- **Estado:** congelado el Día 0 (2/11/2026). Cualquier cambio se discute en el PR, se actualiza este documento, el OpenAPI y el spec.
- **Entrada única:** `http://localhost:8080` (api-gateway). Los servicios no se consumen directo desde el cliente.
- **Prefijos:** `/api/catalog/**`, `/api/inventory/**`, `/api/orders/**`.
- **Internos:** `/internal/**`, solo entre servicios, nunca expuestos por el gateway.

## 1. Convenciones

### 1.1 Identidad (Entrega 1)

| Encabezado | Formato | Cuándo |
|---|---|---|
| `X-User-Id` | UUID | Obligatorio en endpoints protegidos; lo exige el gateway |
| `X-User-Role` | `CLIENT`, `ORGANIZER` o `ADMIN` | Obligatorio en endpoints protegidos |
| `X-Correlation-Id` | UUID o texto corto | Opcional; el gateway lo genera y propaga |
| `Idempotency-Key` | 8 a 100 caracteres | Obligatorio en `POST /api/orders` |

- Header ausente o `X-User-Id` que no sea UUID → **401**.
- Rol no habilitado para la ruta → **403**.
- Las lecturas públicas (búsqueda, detalle, disponibilidad, mapa de asientos) no requieren headers.
- En la Entrega 2 el gateway valida JWT y sigue propagando `X-User-Id`/`X-User-Role`; los servicios no cambian.

### 1.2 Formato de error (RFC 7807)

`Content-Type: application/problem+json`

```json
{
  "type": "https://ticketya.local/errors/seat-not-available",
  "title": "Conflict",
  "status": 409,
  "detail": "El asiento 7f2c... ya no está disponible",
  "instance": "/api/inventory/reservations",
  "code": "SEAT_NOT_AVAILABLE",
  "errors": []
}
```

En errores de validación, `errors[]` trae `field` y `message`:

```json
{
  "type": "https://ticketya.local/errors/validation-error",
  "title": "Bad Request",
  "status": 400,
  "detail": "La solicitud tiene campos inválidos",
  "instance": "/api/catalog/events",
  "code": "VALIDATION_ERROR",
  "errors": [
    { "field": "fecha", "message": "debe ser una fecha futura" }
  ]
}
```

### 1.3 Paginación

Todas las listas paginadas usan la misma forma:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

Parámetros: `page` (desde 0, default 0), `size` (default 20, máximo 100). Un `size` mayor a 100 se recorta a 100.

### 1.4 Fechas, montos e IDs

- IDs: UUID en texto.
- Fechas: ISO-8601 con offset `America/Argentina/Buenos_Aires`; se aceptan y devuelven con offset, por ejemplo `2027-01-15T21:00:00-03:00`.
- Montos: número JSON con dos decimales, en ARS (`15000.00`). Se calculan siempre en el servidor.
- Enums: mayúsculas con guion bajo, tal como se listan en este documento.

### 1.5 Enums congelados

| Enum | Valores |
|---|---|
| `TipoEvento` | `RECITAL`, `TEATRO`, `DEPORTE`, `CONFERENCIA`, `FESTIVAL`, `OTRO` |
| `EstadoEvento` | `BORRADOR`, `PUBLICADO`, `AGOTADO`, `CANCELADO`, `FINALIZADO` |
| `TipoSector` | `NUMERADO`, `NO_NUMERADO` |
| `EstadoReserva` | `VIGENTE`, `CONFIRMADA`, `LIBERADA`, `VENCIDA` |
| `EstadoOrden` | `PENDIENTE_PAGO`, `PAGADA`, `RECHAZADA`, `ERROR_PAGO`, `REINTEGRO_PENDIENTE` |
| `EstadoEntrada` | `VALIDA`, `UTILIZADA`, `DEVUELTA`, `EN_REINTEGRO`, `EN_DEVOLUCION` |
| `EstadoDevolucion` | `PENDIENTE`, `PROCESANDO`, `APROBADA`, `RECHAZADA` |
| `EstadoPago` | `PENDIENTE`, `APROBADO`, `RECHAZADO`, `REINTEGRADO`, `REINTEGRO_PENDIENTE`, `PARCIALMENTE_REINTEGRADO` |
| `MedioPago` | `TARJETA`, `EFECTIVO`, `TRANSFERENCIA` |
| Token de la pasarela mock | `APROBADO`, `RECHAZADO`, `LENTO` |

## 2. Resumen de endpoints

| Método | Ruta | Rol | Éxito | Servicio |
|---|---|---|---|---|
| POST | `/api/catalog/venues` | ADMIN | 201 | catalog |
| GET | `/api/catalog/venues` | público | 200 | catalog |
| GET | `/api/catalog/venues/{venueId}` | público | 200 | catalog |
| POST | `/api/catalog/venues/{venueId}/sectors` | ADMIN | 201 | catalog |
| GET | `/api/catalog/venues/{venueId}/sectors` | público | 200 | catalog |
| POST | `/api/catalog/events` | ORGANIZER | 201 | catalog |
| PUT | `/api/catalog/events/{eventoId}/prices/{sectorId}` | ORGANIZER | 200 | catalog |
| POST | `/api/catalog/events/{eventoId}/publish` | ORGANIZER | 200 | catalog |
| POST | `/api/catalog/events/{eventoId}/cancel` | ADMIN | 200 | catalog |
| GET | `/api/catalog/events` | público | 200 | catalog |
| GET | `/api/catalog/events/{eventoId}` | público | 200 | catalog |
| GET | `/api/inventory/events/{eventoId}/availability` | público | 200 | inventory |
| GET | `/api/inventory/events/{eventoId}/seats` | público | 200 | inventory |
| POST | `/api/inventory/reservations` | autenticado | 201 | inventory |
| GET | `/api/inventory/reservations/{reservaId}` | dueño | 200 | inventory |
| DELETE | `/api/inventory/reservations/{reservaId}` | dueño | 204 | inventory |
| POST | `/api/orders` | autenticado | 201 | orders |
| GET | `/api/orders` | autenticado | 200 | orders |
| GET | `/api/orders/{ordenId}` | dueño | 200 | orders |
| GET | `/api/orders/tickets` | autenticado | 200 | orders |
| GET | `/api/orders/tickets/{entradaId}` | dueño | 200 | orders |
| POST | `/api/orders/tickets/{entradaId}/returns` | dueño | 201 | orders |
| POST | `/api/orders/returns/{devolucionId}/approve` | ADMIN | 200 | orders |
| POST | `/api/orders/returns/{devolucionId}/reject` | ADMIN | 200 | orders |

“Autenticado” significa `CLIENT`, `ORGANIZER` o `ADMIN`; el dueño del recurso se valida dentro del servicio.

## 3. catalog-service

### 3.1 Crear lugar — `POST /api/catalog/venues` (ADMIN)

```json
{
  "nombre": "Estadio Municipal",
  "direccion": "Av. Siempre Viva 742",
  "ciudad": "Tandil",
  "latitud": -37.3217,
  "longitud": -59.1332
}
```

**201 Created** + `Location: /api/catalog/venues/{id}`

```json
{
  "id": "5c9b1f3e-2a4d-4f5e-9a8b-1c2d3e4f5a6b",
  "nombre": "Estadio Municipal",
  "direccion": "Av. Siempre Viva 742",
  "ciudad": "Tandil",
  "latitud": -37.3217,
  "longitud": -59.1332
}
```

Errores: `400 VALIDATION_ERROR` (latitud/longitud fuera de rango, campos vacíos), `403 FORBIDDEN`.

### 3.2 Listar lugares — `GET /api/catalog/venues?page=0&size=20` (público)

**200** con paginación de `VenueResponse`.

### 3.3 Detalle de lugar — `GET /api/catalog/venues/{venueId}` (público)

**200** `VenueResponse`; `404 VENUE_NOT_FOUND`.

### 3.4 Crear sector — `POST /api/catalog/venues/{venueId}/sectors` (ADMIN)

Sector numerado:

```json
{ "nombre": "Platea A", "tipo": "NUMERADO", "filas": 10, "asientosPorFila": 20 }
```

Sector no numerado:

```json
{ "nombre": "Campo", "tipo": "NO_NUMERADO", "capacidad": 5000 }
```

**201 Created**

```json
{
  "id": "9a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d",
  "lugarId": "5c9b1f3e-2a4d-4f5e-9a8b-1c2d3e4f5a6b",
  "nombre": "Platea A",
  "tipo": "NUMERADO",
  "capacidad": 200,
  "filas": 10,
  "asientosPorFila": 20,
  "asientosGenerados": 200
}
```

Errores: `400 VALIDATION_ERROR` (capacidad ≤ 0, sector numerado sin filas, capacidad > 20000, filas > 100 o asientosPorFila > 200), `403`, `404 VENUE_NOT_FOUND`, `409 SECTOR_ALREADY_EXISTS` (ya existe un sector con ese nombre en el lugar).

### 3.5 Listar sectores — `GET /api/catalog/venues/{venueId}/sectors` (público)

**200** con la lista de `SectorResponse`; `404 VENUE_NOT_FOUND`.

### 3.6 Crear evento — `POST /api/catalog/events` (ORGANIZER)

```json
{
  "nombre": "Festival de Verano",
  "descripcion": "Una noche de música",
  "tipo": "FESTIVAL",
  "lugarId": "5c9b1f3e-2a4d-4f5e-9a8b-1c2d3e4f5a6b",
  "fecha": "2027-01-15T21:00:00-03:00",
  "participantes": ["Artista A", "Artista B"],
  "limitePorCliente": 4
}
```

**201 Created**

```json
{
  "id": "1f2e3d4c-5b6a-4978-8a9b-0c1d2e3f4a5b",
  "nombre": "Festival de Verano",
  "descripcion": "Una noche de música",
  "tipo": "FESTIVAL",
  "lugarId": "5c9b1f3e-2a4d-4f5e-9a8b-1c2d3e4f5a6b",
  "organizadorId": "2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e",
  "fecha": "2027-01-15T21:00:00-03:00",
  "estado": "BORRADOR",
  "limitePorCliente": 4,
  "participantes": ["Artista A", "Artista B"],
  "precios": []
}
```

Errores: `400 VALIDATION_ERROR` (fecha pasada, tipo inválido, límite ≤ 0, participantes vacío), `403`, `404 VENUE_NOT_FOUND`.

### 3.7 Asignar precio — `PUT /api/catalog/events/{eventoId}/prices/{sectorId}` (ORGANIZER)

```json
{ "precio": 15000.00 }
```

**200**

```json
{ "eventoId": "1f2e...", "sectorId": "9a1b...", "precio": 15000.00 }
```

Errores: `400 SECTOR_NOT_FROM_VENUE`, `400 INVALID_PRICE` (precio negativo), `403 NOT_EVENT_OWNER`, `404 EVENT_NOT_FOUND`/`SECTOR_NOT_FOUND`, `409 EVENT_NOT_EDITABLE` (evento ya publicado).

### 3.8 Publicar evento — `POST /api/catalog/events/{eventoId}/publish` (ORGANIZER)

Sin cuerpo. **200** `EventResponse` con `estado: "PUBLICADO"`.

Errores: `403 NOT_EVENT_OWNER`, `404 EVENT_NOT_FOUND`, `409 EVENT_NOT_EDITABLE` (no está en `BORRADOR`), `409 EVENT_INCOMPLETE` (algún sector sin precio), `502 INVENTORY_UNAVAILABLE` (inventory no confirmó; el evento queda `BORRADOR`).

### 3.9 Cancelar evento — `POST /api/catalog/events/{eventoId}/cancel` (ADMIN)

Sin cuerpo. **200**

```json
{
  "eventoId": "1f2e...",
  "estado": "CANCELADO",
  "cancelacionPendiente": false
}
```

Catalog cierra primero la venta en inventory con `PATCH /internal/events/{eventoId}/state` y recién después avisa a orders. Si inventory falla, no se avisa a orders y `cancelacionPendiente` queda en `true`; el scheduler reintenta ambos avisos en orden. Si orders falla después de cerrar la venta, también queda pendiente.

Errores: `404 EVENT_NOT_FOUND`, `409 EVENT_NOT_CANCELLABLE` (ya cancelado o finalizado). Un fallo de inventory/orders no cambia el 200: se refleja en `cancelacionPendiente`.

### 3.10 Buscar eventos — `GET /api/catalog/events` (público)

Query params (todos opcionales y combinables):

| Parámetro | Tipo | Descripción |
|---|---|---|
| `tipo` | `TipoEvento` | Tipo exacto |
| `organizadorId` | UUID | Organizador exacto |
| `participante` | texto | Coincidencia parcial, sin distinguir mayúsculas |
| `ciudad` | texto | Ciudad del lugar, coincidencia exacta sin distinguir mayúsculas |
| `fechaDesde` | ISO-8601 | Fecha/hora mínima del evento |
| `fechaHasta` | ISO-8601 | Fecha/hora máxima del evento |
| `orden` | `FECHA_ASC`, `FECHA_DESC`, `PRECIO_ASC`, `PRECIO_DESC`, `RELEVANCIA` | Default `FECHA_ASC` |
| `page`, `size` | entero | Paginación |

Solo devuelve eventos en `PUBLICADO` o `AGOTADO`. Los caracteres `%`, `_` y `\` se tratan como literales (no como comodines de `LIKE`).

**200**

```json
{
  "content": [
    {
      "id": "1f2e3d4c-5b6a-4978-8a9b-0c1d2e3f4a5b",
      "nombre": "Festival de Verano",
      "tipo": "FESTIVAL",
      "ciudad": "Tandil",
      "lugarNombre": "Estadio Municipal",
      "fecha": "2027-01-15T21:00:00-03:00",
      "estado": "PUBLICADO",
      "precioDesde": 8000.00
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

Errores: `400 INVALID_DATE_RANGE` (`fechaDesde` posterior a `fechaHasta`), `400 INVALID_SORT`.

### 3.11 Detalle de evento — `GET /api/catalog/events/{eventoId}` (público)

**200**

```json
{
  "id": "1f2e3d4c-5b6a-4978-8a9b-0c1d2e3f4a5b",
  "nombre": "Festival de Verano",
  "descripcion": "Una noche de música",
  "tipo": "FESTIVAL",
  "lugarId": "5c9b1f3e-2a4d-4f5e-9a8b-1c2d3e4f5a6b",
  "lugarNombre": "Estadio Municipal",
  "ciudad": "Tandil",
  "organizadorId": "2b3c...",
  "fecha": "2027-01-15T21:00:00-03:00",
  "estado": "PUBLICADO",
  "limitePorCliente": 4,
  "participantes": ["Artista A", "Artista B"],
  "sectores": [
    {
      "sectorId": "9a1b...",
      "nombre": "Platea A",
      "tipo": "NUMERADO",
      "precio": 15000.00,
      "capacidad": 200,
      "disponibles": 198
    }
  ]
}
```

`disponibles` se consulta a inventory en el momento. Errores: `404 EVENT_NOT_FOUND` (inexistente o en `BORRADOR`), `503 INVENTORY_UNAVAILABLE`.

### 3.12 Interno — `PATCH /internal/events/{eventoId}/availability`

Llamado por inventory cuando la disponibilidad llega a 0 o vuelve a ser positiva.

```json
{ "agotado": true }
```

**204**. Idempotente. `404 EVENT_NOT_FOUND` si el evento no existe en catalog.

## 4. inventory-service

OpenAPI: [`../openapi/inventory-openapi.json`](../openapi/inventory-openapi.json). Swagger UI en `http://localhost:8082/swagger-ui.html`.

### 4.0 Interno — estado del evento

`PATCH /internal/events/{eventoId}/state`

Lo llama catalog cuando cancela o finaliza un evento.

```json
{ "estado": "CANCELADO" }
```

Estados aceptados: `CANCELADO`, `FINALIZADO`. **204**, también si ya estaba en ese estado. `404 EVENT_NOT_FOUND`. `400 VALIDATION_ERROR` si el estado no es uno de esos dos. `409 EVENT_NOT_EDITABLE` si el evento ya está en el otro estado terminal.

### 4.1 Disponibilidad — `GET /api/inventory/events/{eventoId}/availability` (público)

**200**

```json
{
  "eventoId": "1f2e3d4c-5b6a-4978-8a9b-0c1d2e3f4a5b",
  "estado": "PUBLICADO",
  "sectores": [
    {
      "sectorId": "9a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d",
      "nombre": "Platea A",
      "tipo": "NUMERADO",
      "precio": 15000.00,
      "capacidad": 200,
      "disponibles": 198
    },
    {
      "sectorId": "7c8d9e0f-1a2b-4c3d-9e4f-5a6b7c8d9e0f",
      "nombre": "Campo",
      "tipo": "NO_NUMERADO",
      "precio": 8000.00,
      "capacidad": 5000,
      "disponibles": 4998
    }
  ]
}
```

`disponibles` en sectores numerados es la cantidad de asientos `LIBRE`.

Errores: `404 EVENT_NOT_FOUND`.

### 4.2 Mapa de asientos — `GET /api/inventory/events/{eventoId}/seats?sectorId={uuid}` (público)

**200**

```json
[
  { "asientoId": "a1b2...", "fila": 1, "numero": 1, "estado": "LIBRE" },
  { "asientoId": "a1b2...", "fila": 1, "numero": 2, "estado": "RESERVADO" }
]
```

Errores: `400 SECTOR_NOT_FROM_EVENT`, `404 EVENT_NOT_FOUND`/`SECTOR_NOT_FOUND`. Para sectores no numerados devuelve lista vacía.

### 4.3 Reservar — `POST /api/inventory/reservations` (autenticado)

```json
{
  "eventoId": "1f2e3d4c-5b6a-4978-8a9b-0c1d2e3f4a5b",
  "items": [
    { "sectorId": "9a1b...", "asientoIds": ["a1b2...", "a1b3..."] },
    { "sectorId": "7c8d...", "cantidad": 2 }
  ]
}
```

Reglas:

- Un item con `asientoIds` es de sector numerado; `cantidad` debe omitirse.
- Un item con `cantidad` es de zona no numerada; `asientoIds` debe omitirse.
- Todo o nada: si un asiento no está libre o la zona no alcanza, no se reserva nada.
- `clienteId` sale de `X-User-Id`.
- Límite por cliente: se toma `pg_advisory_xact_lock(hashtext(eventoId || clienteId))` y se suman `cliente_evento.entradas_vendidas` más las cantidades de reservas `VIGENTE`. Si eso más lo pedido supera `limitePorCliente`, se responde 422 y no se reserva nada.
- Vencimiento: 10 minutos desde la creación.

**201 Created**

```json
{
  "id": "b1c2d3e4-f5a6-4b7c-8d9e-0f1a2b3c4d5e",
  "eventoId": "1f2e3d4c-5b6a-4978-8a9b-0c1d2e3f4a5b",
  "eventoNombre": "Festival de Verano",
  "eventoFecha": "2027-01-15T21:00:00-03:00",
  "clienteId": "2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e",
  "estado": "VIGENTE",
  "expiresAt": "2026-11-02T18:10:00-03:00",
  "items": [
    {
      "sectorId": "9a1b...",
      "asientoId": "a1b2...",
      "cantidad": 1,
      "precioUnitario": 15000.00,
      "subtotal": 15000.00
    },
    {
      "sectorId": "7c8d...",
      "asientoId": null,
      "cantidad": 2,
      "precioUnitario": 8000.00,
      "subtotal": 16000.00
    }
  ],
  "total": 31000.00
}
```

Errores: `400 VALIDATION_ERROR`, `400 SECTOR_MISMATCH` (el asiento no pertenece al sector del ítem), `404 EVENT_NOT_FOUND`/`SECTOR_NOT_FOUND`, `409 SEAT_NOT_AVAILABLE`, `409 INSUFFICIENT_AVAILABILITY`, `409 EVENT_NOT_ON_SALE` (cancelado), `409 EVENT_FINISHED` (la fecha del evento ya pasó), `422 CLIENT_LIMIT_EXCEEDED`, `503 LOCK_TIMEOUT` (con `Retry-After: 1`).

### 4.4 Consultar reserva — `GET /api/inventory/reservations/{reservaId}` (dueño)

**200** `ReservationResponse`. Si está vencida libera los lugares y devuelve `estado: "VENCIDA"` (no 410 en la consulta; 410 se usa al pagar). Errores: `403 NOT_RESERVATION_OWNER`, `404 RESERVATION_NOT_FOUND`.

### 4.5 Cancelar reserva — `DELETE /api/inventory/reservations/{reservaId}` (dueño)

**204 No Content**. Idempotente: si ya estaba liberada/vencida, también 204. Errores: `403`, `404`.

### 4.6 Interno — alta/actualización de inventario

`POST /internal/events/{eventoId}/inventory`

```json
{
  "eventoId": "1f2e3d4c-5b6a-4978-8a9b-0c1d2e3f4a5b",
  "nombre": "Festival de Verano",
  "fecha": "2027-01-15T21:00:00-03:00",
  "limitePorCliente": 4,
  "version": 1,
  "sectores": [
    {
      "sectorId": "9a1b...",
      "nombre": "Platea A",
      "tipo": "NUMERADO",
      "precio": 15000.00,
      "capacidad": 200,
      "filas": 10,
      "asientos": [
        { "asientoId": "a1b2...", "fila": 1, "numero": 1 }
      ]
    }
  ]
}
```

**200**

```json
{ "eventoId": "1f2e...", "sectores": 1, "asientos": 200, "idempotente": true }
```

Idempotente por `eventoId` + `version`: reintentar no duplica filas ni reinicia disponibilidad ya vendida.

### 4.6.1 Interno — cambiar estado del evento

`PATCH /internal/events/{eventoId}/state`

```json
{ "estado": "CANCELADO" }
```

**204 No Content**. Catalog lo llama antes de cancelar (`CANCELADO`) o finalizar (`FINALIZADO`) para que inventory deje de vender. Idempotente.

### 4.7 Interno — consultar reserva para orders

`GET /internal/reservations/{reservaId}` → **200** `ReservationResponse`; `404 RESERVATION_NOT_FOUND`.

Además de los campos de la consulta pública (4.4), `ReservationResponse` incluye `eventoNombre` y `eventoFecha` para que orders pueda guardar el snapshot del evento sin volver a llamar a catalog:

```json
{
  "id": "b1c2d3e4-f5a6-4b7c-8d9e-0f1a2b3c4d5e",
  "eventoId": "1f2e3d4c-5b6a-4978-8a9b-0c1d2e3f4a5b",
  "eventoNombre": "Festival de Verano",
  "eventoFecha": "2027-01-15T21:00:00-03:00",
  "clienteId": "2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e",
  "estado": "VIGENTE",
  "expiresAt": "2026-11-02T18:10:00-03:00",
  "items": [],
  "total": 0.00
}
```

### 4.8 Interno — confirmar venta

`POST /internal/reservations/{reservaId}/confirm` → **200** reserva `CONFIRMADA`, asientos `VENDIDO`.

Errores: `409 RESERVATION_NOT_CONFIRMABLE`, `410 RESERVATION_EXPIRED`. Idempotente: repetir sobre una reserva ya `CONFIRMADA` devuelve 200.

### 4.9 Interno — liberar reserva

`POST /internal/reservations/{reservaId}/release`

```json
{ "motivo": "PAGO_RECHAZADO" }
```

**204**, idempotente.

### 4.10 Interno — liberar por devolución

`POST /internal/returns`

```json
{
  "eventoId": "1f2e...",
  "clienteId": "2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e",
  "referencia": "f1a2b3c4-d5e6-4f7a-8b9c-0d1e2f3a4b5c",
  "items": [
    { "sectorId": "9a1b...", "asientoId": "a1b2...", "cantidad": 1 }
  ]
}
```

**204**, idempotente. Libera asientos vendidos o repone disponibilidad de zonas.
`referencia` es opcional; orders envía el id de la devolución para que un reintento
no reponga dos veces la disponibilidad de una zona no numerada.

Header opcional `Idempotency-Key`. Si orders reintenta la misma devolución, manda la misma clave: la segunda llamada no vuelve a sumar lugares. Sin la clave, un asiento igual no se libera dos veces (solo pasa de `VENDIDO` a `LIBRE` una vez); la zona sí necesita la clave para no reponerse dos veces.

`clienteId` lo manda orders con el dueño de la entrada. En una zona, además de sumar `disponibles`, inventory resta esa cantidad de `cliente_evento.entradas_vendidas` (`GREATEST(0, …)`). Si hay algún ítem de zona (`asientoId` null) y falta `clienteId`, responde `400 CLIENT_ID_REQUIRED` y no aplica la devolución. Un asiento vendido sigue liberando el cupo con el cliente de su reserva, aunque `clienteId` falte. Errores: `400 CLIENT_ID_REQUIRED`, `400 VALIDATION_ERROR`.

## 5. orders-service

### 5.1 Crear orden y pagar — `POST /api/orders` (autenticado)

Headers: `Idempotency-Key: <clave>`.

```json
{
  "reservaId": "b1c2d3e4-f5a6-4b7c-8d9e-0f1a2b3c4d5e",
  "medioPago": "TARJETA",
  "token": "APROBADO"
}
```

`token` es el resultado simulado de la pasarela: `APROBADO`, `RECHAZADO` o `LENTO`.

**201 Created** (también cuando el pago se rechaza: la orden existe en `RECHAZADA`)

```json
{
  "id": "c1d2e3f4-a5b6-4c7d-8e9f-0a1b2c3d4e5f",
  "clienteId": "2b3c...",
  "reservaId": "b1c2...",
  "eventoId": "1f2e...",
  "eventoNombre": "Festival de Verano",
  "eventoFecha": "2027-01-15T21:00:00-03:00",
  "estado": "PAGADA",
  "medioPago": "TARJETA",
  "total": 31000.00,
  "items": [
    { "sectorId": "9a1b...", "asientoId": "a1b2...", "cantidad": 1, "precioUnitario": 15000.00, "subtotal": 15000.00 }
  ],
  "entradas": [
    { "id": "d1e2...", "qrCode": "e1f2...", "estado": "VALIDA", "sectorId": "9a1b...", "asientoId": "a1b2..." }
  ],
  "createdAt": "2026-11-02T18:00:05-03:00"
}
```

Errores: `400 VALIDATION_ERROR`, `403 NOT_RESERVATION_OWNER`, `404 RESERVATION_NOT_FOUND`, `409 IDEMPOTENCY_IN_PROGRESS` (con `Retry-After`), `410 RESERVATION_EXPIRED`, `422 IDEMPOTENCY_KEY_REUSED` (misma clave, cuerpo distinto), `502 PAYMENT_GATEWAY_ERROR`.

### 5.2 Listar mis órdenes — `GET /api/orders?page=0&size=20` (autenticado)

**200** paginado de `OrderResponse` del cliente autenticado, más recientes primero.

### 5.3 Detalle de orden — `GET /api/orders/{ordenId}` (dueño)

**200** `OrderResponse`; `403 ORDER_NOT_OWNER`, `404 ORDER_NOT_FOUND`.

### 5.4 Mis entradas — `GET /api/orders/tickets?eventoId={uuid}&page=0&size=20` (autenticado)

**200** paginado:

```json
{
  "content": [
    {
      "id": "d1e2...",
      "eventoId": "1f2e...",
      "eventoNombre": "Festival de Verano",
      "eventoFecha": "2027-01-15T21:00:00-03:00",
      "sectorId": "9a1b...",
      "asientoId": "a1b2...",
      "qrCode": "e1f2...",
      "estado": "VALIDA",
      "createdAt": "2026-11-02T18:00:05-03:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

### 5.5 Detalle de entrada — `GET /api/orders/tickets/{entradaId}` (dueño)

**200** `TicketResponse`; `403 NOT_TICKET_OWNER`, `404 TICKET_NOT_FOUND`.

### 5.6 Solicitar devolución — `POST /api/orders/tickets/{entradaId}/returns` (dueño)

```json
{ "motivo": "No puedo asistir" }
```

**201 Created**

```json
{
  "id": "f1a2b3c4-d5e6-4f7a-8b9c-0d1e2f3a4b5c",
  "entradaId": "d1e2...",
  "estado": "PENDIENTE",
  "motivo": "No puedo asistir",
  "createdAt": "2026-11-02T18:05:00-03:00",
  "resolvedAt": null
}
```

Errores: `403 NOT_TICKET_OWNER`, `404 TICKET_NOT_FOUND`, `409 TICKET_NOT_VALID` (utilizada/devuelta/en reintegro/en devolución), `409 RETURN_ALREADY_EXISTS`, `422 RETURN_WINDOW_CLOSED` (faltan 72 h o menos).

### 5.7 Aprobar devolución — `POST /api/orders/returns/{devolucionId}/approve` (ADMIN)

Sin cuerpo. **200** `ReturnResponse` con `estado: "APROBADA"`. Marca la entrada `EN_DEVOLUCION`, solicita el reintegro y libera el lugar en inventory.

Si falla la pasarela o inventory, responde `502` (`PAYMENT_GATEWAY_ERROR` o `INVENTORY_UNAVAILABLE`), la devolución queda `PROCESANDO` y la entrada `EN_DEVOLUCION`; los schedulers de reintento la terminan.

Errores: `404 RETURN_NOT_FOUND`, `409 RETURN_ALREADY_RESOLVED`, `502 PAYMENT_GATEWAY_ERROR`/`INVENTORY_UNAVAILABLE` (queda `PROCESANDO` y se reintenta).

### 5.8 Rechazar devolución — `POST /api/orders/returns/{devolucionId}/reject` (ADMIN)

Sin cuerpo. **200** `ReturnResponse` con `estado: "RECHAZADA"`; la entrada sigue `VALIDA`.

Errores: `404 RETURN_NOT_FOUND`, `409 RETURN_ALREADY_RESOLVED`, `409 RETURN_IN_PROGRESS` (la devolución está `PROCESANDO` y no se puede rechazar).

### 5.9 Interno — cancelar evento

`POST /internal/events/{eventoId}/cancel` (llamado por catalog)

**200**

```json
{ "eventoId": "1f2e...", "entradasAfectadas": 120, "reintegrosSolicitados": 120 }
```

Marca las entradas vendidas como `EN_REINTEGRO` y solicita el refund. Idempotente: repetir devuelve las mismas entradas ya afectadas sin duplicar reintegros.

## 6. Flujo de compra de punta a punta

```bash
GATEWAY=http://localhost:8080
CLIENTE=2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e

# 1) Buscar
curl "$GATEWAY/api/catalog/events?ciudad=Tandil&orden=FECHA_ASC"

# 2) Disponibilidad y asientos
curl "$GATEWAY/api/inventory/events/$EVENTO/availability"
curl "$GATEWAY/api/inventory/events/$EVENTO/seats?sectorId=$SECTOR"

# 3) Reservar (10 minutos)
curl -X POST "$GATEWAY/api/inventory/reservations" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: $CLIENTE" -H "X-User-Role: CLIENT" \
  -d '{"eventoId":"'"$EVENTO"'","items":[{"sectorId":"'"$SECTOR"'","asientoIds":["'"$ASIENTO"'"]}]}'

# 4) Pagar (idempotente)
curl -X POST "$GATEWAY/api/orders" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: compra-$RESERVA" \
  -H "X-User-Id: $CLIENTE" -H "X-User-Role: CLIENT" \
  -d '{"reservaId":"'"$RESERVA"'","medioPago":"TARJETA","token":"APROBADO"}'

# 5) Ver entradas
curl "$GATEWAY/api/orders/tickets" \
  -H "X-User-Id: $CLIENTE" -H "X-User-Role: CLIENT"

# 6) Devolver una entrada (si faltan más de 72 h)
curl -X POST "$GATEWAY/api/orders/tickets/$ENTRADA/returns" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: $CLIENTE" -H "X-User-Role: CLIENT" \
  -d '{"motivo":"No puedo asistir"}'
```

## 7. Catálogo de errores

| HTTP | `code` | Cuándo |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Campos o parámetros inválidos; incluye `errors[]` |
| 400 | `MALFORMED_JSON` | Cuerpo no parseable |
| 400 | `INVALID_DATE_RANGE` | `fechaDesde` posterior a `fechaHasta` |
| 400 | `INVALID_SORT` | `orden` no soportado |
| 400 | `INVALID_PRICE` | Precio negativo |
| 400 | `SECTOR_NOT_FROM_VENUE` | Sector ajeno al lugar del evento |
| 400 | `SECTOR_NOT_FROM_EVENT` | Sector ajeno al evento consultado |
| 401 | `UNAUTHORIZED` | Headers ausentes, UUID inválido o rol desconocido |
| 403 | `FORBIDDEN` | Rol sin permiso para la ruta |
| 403 | `NOT_EVENT_OWNER` | El evento es de otro organizador |
| 403 | `NOT_RESERVATION_OWNER` | La reserva es de otro cliente |
| 403 | `ORDER_NOT_OWNER` | La orden es de otro cliente |
| 403 | `NOT_TICKET_OWNER` | La entrada es de otro cliente |
| 404 | `VENUE_NOT_FOUND` | Lugar inexistente |
| 404 | `SECTOR_NOT_FOUND` | Sector inexistente |
| 404 | `EVENT_NOT_FOUND` | Evento inexistente o no visible |
| 404 | `RESERVATION_NOT_FOUND` | Reserva inexistente |
| 404 | `ORDER_NOT_FOUND` | Orden inexistente |
| 404 | `TICKET_NOT_FOUND` | Entrada inexistente |
| 404 | `RETURN_NOT_FOUND` | Devolución inexistente |
| 404 | `NOT_FOUND` | Ruta inexistente |
| 405 | `METHOD_NOT_ALLOWED` | Método HTTP no permitido |
| 409 | `EVENT_NOT_EDITABLE` | Evento ya publicado o en estado no editable |
| 409 | `EVENT_INCOMPLETE` | Faltan precios para publicar |
| 409 | `EVENT_NOT_CANCELLABLE` | Evento finalizado o ya cancelado |
| 409 | `EVENT_NOT_ON_SALE` | Evento cancelado/finalizado al reservar |
| 409 | `SEAT_NOT_AVAILABLE` | Asiento reservado o vendido |
| 409 | `INSUFFICIENT_AVAILABILITY` | Zona sin lugares suficientes |
| 409 | `RESERVATION_NOT_CONFIRMABLE` | Reserva liberada o vencida al confirmar |
| 409 | `TICKET_NOT_VALID` | Entrada no válida para devolución |
| 409 | `RETURN_ALREADY_EXISTS` | Ya hay una devolución abierta |
| 409 | `RETURN_ALREADY_RESOLVED` | La devolución ya fue resuelta |
| 409 | `IDEMPOTENCY_IN_PROGRESS` | Reintento mientras la compra original corre (incluye `Retry-After`) |
| 410 | `RESERVATION_EXPIRED` | Se intenta pagar una reserva vencida |
| 422 | `CLIENT_LIMIT_EXCEEDED` | Supera el límite de entradas por cliente |
| 422 | `IDEMPOTENCY_KEY_REUSED` | Misma `Idempotency-Key` con cuerpo distinto |
| 422 | `RETURN_WINDOW_CLOSED` | Faltan 72 h o menos para el evento |
| 502 | `INVENTORY_UNAVAILABLE` | catalog/orders no pudo hablar con inventory |
| 502 | `ORDERS_UNAVAILABLE` | catalog no pudo avisar la cancelación a orders |
| 502 | `PAYMENT_GATEWAY_ERROR` | La pasarela mock falló o expiró |
| 503 | `INVENTORY_UNAVAILABLE` | No se pudo obtener disponibilidad en el detalle |
| 500 | `INTERNAL_ERROR` | Error inesperado; no expone stacktrace |

## 8. Reglas de cambio

1. Un endpoint nuevo o un cambio de payload empieza por el spec de OpenSpec y este documento.
2. El PR actualiza el OpenAPI y el README del servicio en el mismo commit.
3. Los códigos de error son estables: no se renombran sin actualizar `docs/pruebas.md` y la colección Postman.
4. Los endpoints internos no se exponen en el gateway ni en la colección pública de Postman.
5. Los ejemplos de este documento son la base de los tests de contrato y de la colección Postman (5.2).
