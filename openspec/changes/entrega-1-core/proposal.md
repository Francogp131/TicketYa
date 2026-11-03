## Why

TicketYa necesita su núcleo funcional: publicar eventos, buscarlos y comprar entradas sin que una misma entrada se venda dos veces. Es la base de la Entrega 1 del TP.

## What Changes

- Gestión de lugares, sectores, eventos, precios y publicación (catalog-service).
- Búsqueda de eventos por múltiples criterios con orden y paginación (catalog-service).
- Reserva temporal de lugares, mecanismo anti doble venta y límite por cliente (inventory-service).
- Compra con pago simulado, emisión de entradas con QR e idempotencia (orders-service).
- Devoluciones (hasta 72 h antes) y cancelación de eventos con reintegro (orders-service).
- API Gateway como punto de entrada único. Usuario identificado por `X-User-Id` y `X-User-Role`.

## Capabilities

### New Capabilities
- `gestion-eventos-lugares`: lugares, sectores, eventos, precios y publicación.
- `busqueda-eventos`: búsqueda multi-criterio con orden y paginación.
- `reserva-y-compra`: reserva temporal, compra, QR, no doble venta e idempotencia.
- `devoluciones-cancelacion`: devoluciones de entradas y cancelación de eventos.

### Modified Capabilities
Ninguna.

## Impact

Servicios nuevos: `catalog-service`, `inventory-service`, `orders-service`, `api-gateway`. Una base relacional por servicio.

## Fuera de alcance (Entrega 2)

Fila virtual, control de acceso con QR, recomendaciones, notificaciones, reportes, JWT, MongoDB, saga completa, cache, log centralizado y Docker por servicio.
