# Arquitectura — TicketYa Entrega 1

Este documento resume los límites de servicio, las dependencias y el flujo de compra.
El detalle técnico completo está en [`../openspec/changes/entrega-1-core/design.md`](../openspec/changes/entrega-1-core/design.md)
y el contrato en [`api-contract.md`](api-contract.md).

## 1. Diagrama de módulos

```mermaid
flowchart LR
    subgraph Cliente
        U[Usuario / Postman / curl]
    end

    G[api-gateway<br/>:8080]

    subgraph Catalog[catalog-service :8081]
        C1[Lugares y sectores]
        C2[Eventos y precios]
        C3[Publicación y estados]
        C4[Búsqueda]
    end

    subgraph Inventory[inventory-service :8082]
        I1[Disponibilidad]
        I2[Reservas y límites]
        I3[Confirmación y liberación]
    end

    subgraph Orders[orders-service :8083]
        O1[Órdenes]
        O2[Pasarela mock]
        O3[Entradas y QR]
        O4[Idempotencia y compensaciones]
    end

    CDB[(catalog_db<br/>PostgreSQL :5433)]
    IDB[(inventory_db<br/>PostgreSQL :5434)]
    ODB[(orders_db<br/>PostgreSQL :5435)]
    PAGO[[Pasarela mock<br/>APROBADO / RECHAZADO / LENTO]]

    U -->|HTTP| G
    G -->|/api/catalog/**| Catalog
    G -->|/api/inventory/**| Inventory
    G -->|/api/orders/**| Orders

    Catalog --> CDB
    Inventory --> IDB
    Orders --> ODB

    Catalog -->|POST /internal/events/{id}/inventory| Inventory
    Catalog -->|POST /internal/events/{id}/cancel| Orders
    Orders -->|GET /internal/reservations/{id}| Inventory
    Orders -->|POST confirm / release / returns| Inventory
    Orders -->|cobrar| PAGO
```

- El cliente nunca llama a los servicios por dentro: todo entra por el gateway.
- Cada servicio es dueño de su base y no comparte tablas.
- Las dependencias internas son REST y usan los endpoints `/internal/**`.

## 2. Secuencia de compra de una entrada numerada

Cubre los tres resultados de la pasarela y la reserva vencida (tareas 4.3 a 4.7).

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente
    participant G as Gateway
    participant I as inventory-service
    participant O as orders-service
    participant P as Pasarela mock
    participant DB as orders_db

    C->>G: POST /api/inventory/reservations
    G->>I: POST /api/inventory/reservations
    I->>I: Bloquea asientos LIBRE y descuenta zona
    I-->>G: 201 Reserva VIGENTE (expira en 10 min)
    G-->>C: 201 Reserva

    C->>G: POST /api/orders + Idempotency-Key
    G->>O: POST /api/orders
    O->>DB: INSERT idempotency_key EN_PROCESO
    O->>I: GET /internal/reservations/{id}
    I-->>O: Reserva VIGENTE
    alt Reserva vencida
        O-->>G: 410 RESERVATION_EXPIRED
        G-->>C: 410
    else Reserva vigente
        O->>DB: INSERT orden PENDIENTE_PAGO + items + pago
        O->>P: cobrar(monto, token)
        alt Pago APROBADO
            P-->>O: APROBADO
            O->>I: POST /internal/reservations/{id}/confirm
            alt Confirmación OK
                I-->>O: Reserva CONFIRMADA y asientos VENDIDO
                O->>DB: orden PAGADA + entradas con QR único
                O->>DB: idempotency_key COMPLETADA
                O-->>G: 201 Orden PAGADA + entradas
                G-->>C: 201
            else Falla la confirmación o la emisión de entradas
                O->>DB: orden REINTEGRO_PENDIENTE + pago REINTEGRADO
                O->>I: liberar o devolver lugares
                O->>DB: idempotency_key COMPLETADA
                O-->>G: 201 Orden REINTEGRO_PENDIENTE sin entradas
                G-->>C: 201
            end
        else Pago RECHAZADO
            P-->>O: RECHAZADO
            O->>DB: orden RECHAZADA + pago RECHAZADO
            O->>I: POST /internal/reservations/{id}/release
            I-->>O: 204 y lugares LIBRE
            O->>DB: idempotency_key COMPLETADA
            O-->>G: 201 Orden RECHAZADA sin entradas
            G-->>C: 201
        else Pasarela LENTO / timeout
            P--x O: timeout
            O->>DB: orden ERROR_PAGO + pago RECHAZADO
            O->>I: POST /internal/reservations/{id}/release
            O->>DB: DELETE idempotency_key EN_PROCESO
            O-->>G: 502 PAYMENT_GATEWAY_ERROR
            G-->>C: 502
        end
    end
```

## 3. Publicación de un evento

```mermaid
sequenceDiagram
    autonumber
    actor A as Organizador
    participant G as Gateway
    participant C as catalog-service
    participant I as inventory-service

    A->>G: POST /api/catalog/events/{id}/publish
    G->>C: publish
    C->>C: Valida BORRADOR y precios completos
    C->>I: POST /internal/events/{id}/inventory (snapshot)
    I->>I: Upsert idempotente por eventoId + version
    alt Inventory responde
        I-->>C: 200
        C->>C: Estado PUBLICADO
        C-->>G: 200 Evento PUBLICADO
    else Inventory falla
        C-->>G: 502 INVENTORY_UNAVAILABLE
        Note over C: El evento sigue en BORRADOR
    end
```

## 4. Cancelación de evento y devolución

```mermaid
sequenceDiagram
    autonumber
    actor A as Admin
    participant G as Gateway
    participant C as catalog-service
    participant O as orders-service
    participant I as inventory-service

    A->>G: POST /api/catalog/events/{id}/cancel
    G->>C: cancel
    C->>C: Estado CANCELADO
    C->>O: POST /internal/events/{id}/cancel
    alt Orders responde
        O->>O: Entradas VALIDA → EN_REINTEGRO
        O-->>C: 200 {entradasAfectadas}
        C->>C: cancelacionPendiente = false
    else Orders no responde
        C->>C: cancelacionPendiente = true
        Note over C: El scheduler reintenta cada 60 s
    end
    C-->>G: 200 {estado, cancelacionPendiente}
```

La devolución aprobada (Día 4) reutiliza el endpoint interno
`POST /internal/returns` de inventory con una `referencia` para que la reposición
de zonas no numeradas sea idempotente.

## 5. Decisiones de consistencia

| Situación | Mecanismo |
|---|---|
| Doble venta de asientos | `SELECT ... FOR UPDATE` ordenado por `asientoId` dentro de la transacción |
| Sobreventa de zonas | `UPDATE ... WHERE disponibles >= N RETURNING` |
| Reintento de compra | `idempotency_key` con claim `EN_PROCESO` y respuesta guardada |
| Pago aprobado con fallo posterior | Orden `REINTEGRO_PENDIENTE`, pago `REINTEGRADO` y liberación/devolución |
| Cancelación sin orders | `cancelacion_pendiente` + scheduler de reintento |
| Reserva vencida | Scheduler cada 30 s y validación perezosa al consultar/pagar |
