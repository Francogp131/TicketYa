# Pruebas — TicketYa Entrega 1

Este documento reúne la estrategia, los comandos y la evidencia de las pruebas de los Días 2 a 5.
Los comandos se ejecutan desde `ticketya/` salvo que se indique otro directorio.

## 1. Estrategia

| Nivel | Qué cubre | Herramienta |
|---|---|---|
| Unitario de servicio | Reglas de negocio, estados, límites, compensaciones | JUnit 5 + Mockito |
| Controller | Contrato HTTP, validación, identidad y códigos RFC 7807 | `@WebMvcTest` + MockMvc |
| Integración con PostgreSQL | Migraciones Flyway, locks, idempotencia y concurrencia | Spring Boot + Testcontainers |
| E2E por gateway | Flujo reservar → pagar → QR, rechazo, vencimiento y cancelación | curl/Postman contra Docker Compose |

Requisitos: Java 21, Maven 3.9+, Docker Desktop encendido. Si Maven usa otra JDK:

```bash
export JAVA_HOME=/Users/Franco/Library/Java/JavaVirtualMachines/temurin-21.0.12.1/Contents/Home
```

## 2. Comandos por servicio

```bash
# Catálogo (incluye búsqueda contra PostgreSQL real con Testcontainers)
cd catalog-service && mvn clean test

# Inventario (unitarios + concurrencia Testcontainers)
cd inventory-service && mvn clean test

# Órdenes (unitarios + idempotencia concurrente Testcontainers)
cd orders-service && mvn clean test

# Gateway
cd api-gateway && mvn clean test
```

Los tests con Testcontainers se omiten automáticamente si Docker no está disponible
(`@Testcontainers(disabledWithoutDocker = true)`), salvo en CI, donde el runner lo tiene.

## 3. Evidencia de la suite

Suite completa del Día 5 (7/11/2026, `mvn clean test` con Java 21 y Docker encendido):

| Proyecto | Tests | Resultado |
|---|---|---|
| catalog-service | 54 | 0 fallos, 0 errores, 0 omitidos |
| inventory-service | 30 | 0 fallos, 0 errores, 0 omitidos |
| orders-service | 46 | 0 fallos, 0 errores, 0 omitidos |
| api-gateway | 5 | 0 fallos, 0 errores, 0 omitidos |
| **Total** | **135** | **0 fallos, 0 errores, 0 omitidos** |

Evidencia: [`docs/evidencia/dia-5-suite.txt`](evidencia/dia-5-suite.txt). Incluye los tests de Testcontainers de catalog (búsqueda), inventory (concurrencia, vencimiento, límite y confirmación) y orders (idempotencia), por lo que ningún test quedó omitido.

Desglose relevante:

- `EventServiceTest` (22): estados, cancelación con/sin orders, callback de disponibilidad,
  scheduler de finalización, búsqueda por fecha/precio/relevancia, rango y orden inválidos.
- `EventoBusquedaIntegrationTest` (3): búsqueda sin filtros, texto con mayúsculas y filtros
  combinados contra PostgreSQL real (regresión del 500 por parámetros nulos).
- `OrdersRestClientTest` (3): contrato interno de cancelación, propagación de 5xx sin reintentos
  y corte por timeout del cliente de catalog hacia orders.
- `ReservaServiceTest` (6): asientos, zona sin disponibilidad, límite, vencimiento,
  confirmación/liberación y devoluciones idempotentes.
- `ConcurrenciaPostgresTest` (2): 100 hilos sobre el mismo asiento y 20 hilos sobre el último
  lugar de una zona; en ambos casos exactamente un ganador.
- `OrderServiceTest` (15): pago aprobado, rechazado, timeout, compensación por confirmación
  y por emisión de entradas, idempotencia, errores de reserva y cancelación de evento con
  reintegro de pagos.
- `DevolucionServiceTest` (8): solicitud dentro/fuera de plazo, entrada ajena/usada,
  aprobación con liberación + invalidación + refund, fallo de inventory deja PROCESANDO,
  rechazo mantiene la entrada VALIDA y resolución duplicada.
- `ReturnControllerTest` (3): 401 sin identidad, 403 para cliente y 200 para ADMIN.
- `OrderQueryServiceTest` (5): listado paginado del cliente, filtro por evento, 403/404 de
  entrada y página inválida.
- `TicketControllerTest` (3): 401 sin identidad, página de entradas y 403 de entrada ajena.
- `IdempotencyServiceTest` (5): misma respuesta, 422 con cuerpo distinto, 409 en proceso,
  hash determinista.
- `OrderIdempotencyIntegrationTest` (2): dos reintentos concurrentes crean una sola orden y
  el reintento secuencial devuelve la misma orden y las mismas entradas.
- `TicketServiceTest` (1): una entrada con QR único por lugar confirmado.

## 4. Prueba de concurrencia (tarea 3.7)

```bash
cd inventory-service
mvn -Dtest=ConcurrenciaPostgresTest test
```

La prueba levanta PostgreSQL 16 con Testcontainers, crea un evento y:

1. Lanza 100 hilos que intentan reservar el mismo asiento. Espera **1 éxito** y el resto
   `SEAT_NOT_AVAILABLE`; la base debe tener **una** reserva y el asiento `RESERVADO`.
2. Lanza 20 hilos sobre una zona con un solo lugar. Espera **1 éxito**, el resto
   `INSUFFICIENT_AVAILABILITY`, y `disponibles = 0`.

Salida esperada:

```
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
```

## 5. Idempotencia de compra (tarea 4.6)

```bash
cd orders-service
mvn -Dtest=OrderIdempotencyIntegrationTest test
```

- Dos hilos con la misma `Idempotency-Key` y el mismo cuerpo: uno crea la orden y el otro
  recibe la respuesta guardada o `409 IDEMPOTENCY_IN_PROGRESS`. La tabla `orden` termina con
  una sola fila.
- Un reintento posterior devuelve el mismo `id` y las mismas entradas.

## 6. E2E por gateway

La colección Postman se ejecuta completa con Newman:

```bash
npx -y newman run postman/TicketYa.postman_collection.json --reporters cli
```

Resultado de la última corrida (6/11/2026): **45 requests, 45 assertions, 0 fallos**.
Incluye catálogo, búsqueda, publicación, reserva numerada y de zona, compra aprobada con QR,
reintento idempotente, reuso de clave, pago rechazado, pago lento, consultas propias,
devolución aprobada, devolución rechazada y cancelación.

Evidencia: [`docs/evidencia/dia-4-newman.txt`](evidencia/dia-4-newman.txt).

Levantar bases y servicios:

```bash
docker compose -f docker-compose.dev.yml up -d
# en cuatro terminales, con Java 21:
(cd catalog-service && mvn spring-boot:run)
(cd inventory-service && mvn spring-boot:run)
(cd orders-service && mvn spring-boot:run)
(cd api-gateway && mvn spring-boot:run)
```

Variables del seed:

```bash
GATEWAY=http://localhost:8080
CLIENTE=33333333-3333-4333-8333-333333333333
EVENTO=d0000000-0000-4000-8000-000000000001
SECTOR=b0000000-0000-4000-8000-000000000001
ASIENTO=c0000000-0000-4000-8000-010101000000
```

### 6.1 Flujo aprobado

```bash
curl "$GATEWAY/api/catalog/events?ciudad=Tandil&orden=PRECIO_ASC"

RESERVA=$(curl -s -X POST "$GATEWAY/api/inventory/reservations" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: $CLIENTE" -H "X-User-Role: CLIENT" \
  -d '{"eventoId":"'"$EVENTO"'","items":[{"sectorId":"'"$SECTOR"'","asientoIds":["'"$ASIENTO"'"]}]}' | jq -r .id)

curl -s -X POST "$GATEWAY/api/orders" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: compra-$RESERVA" \
  -H "X-User-Id: $CLIENTE" -H "X-User-Role: CLIENT" \
  -d '{"reservaId":"'"$RESERVA"'","medioPago":"TARJETA","token":"APROBADO"}' | jq '{id,estado,entradas}'
```

Resultado observado: `201`, `estado: "PAGADA"` y una entrada `VALIDA` con `qrCode` único.

### 6.2 Reintento idempotente

Repetir el `POST /api/orders` con la misma clave devuelve el mismo `id`; cambiar el cuerpo
con la misma clave devuelve `422 IDEMPOTENCY_KEY_REUSED`.

### 6.3 Pago rechazado

```bash
curl -s -X POST "$GATEWAY/api/orders" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: rechazo-$RESERVA" \
  -H "X-User-Id: $CLIENTE" -H "X-User-Role: CLIENT" \
  -d '{"reservaId":"'"$RESERVA"'","medioPago":"TARJETA","token":"RECHAZADO"}'
```

Resultado observado: `201` con `estado: "RECHAZADA"`, `entradas: []`; la disponibilidad del
sector vuelve al valor anterior.

### 6.4 Pago lento / vencida

- `token: "LENTO"` responde `502 PAYMENT_GATEWAY_ERROR`, libera la reserva y no deja orden pagada.
- Una reserva con `expires_at` vencido responde `410 RESERVATION_EXPIRED`; el scheduler y la
  validación perezosa liberan los lugares.

### 6.5 Cancelación

```bash
ADMIN=11111111-1111-4111-8111-111111111111
curl -s -X POST "$GATEWAY/api/catalog/events/$EVENTO/cancel" \
  -H "X-User-Id: $ADMIN" -H "X-User-Role: ADMIN"
```

Resultado observado: `estado: "CANCELADO"`, `cancelacionPendiente: false` y las entradas del
evento pasan a `EN_REINTEGRO` en `orders_db`; los pagos aprobados pasan a `REINTEGRADO`.
Repetir el endpoint interno devuelve la misma cantidad de entradas afectadas.

### 6.6 Devoluciones

- `POST /api/orders/tickets/{entradaId}/returns` (dueño) crea la solicitud `PENDIENTE`;
  fuera de las 72 horas responde `422 RETURN_WINDOW_CLOSED`.
- `POST /api/orders/returns/{id}/approve` (ADMIN) libera el lugar en inventory, pasa la
  entrada a `DEVUELTA`, el pago a `REINTEGRADO` y la solicitud a `APROBADA`.
- `POST /api/orders/returns/{id}/reject` (ADMIN) deja la solicitud `RECHAZADA` y la entrada
  sigue `VALIDA`.
- Repetir approve/reject sobre una solicitud resuelta responde `409 RETURN_ALREADY_RESOLVED`.

La colección Postman cubre los dos caminos (aprobar la primera entrada y rechazar la segunda)
y verifica el estado final de cada entrada en el detalle de la orden.

### 6.7 Consultas propias (4.10)

- `GET /api/orders?page=0&size=5` devuelve solo las órdenes del `X-User-Id`.
- `GET /api/orders/tickets?eventoId={uuid}&page=0&size=5` devuelve solo las entradas propias.
- `GET /api/orders/tickets/{entradaId}` devuelve la entrada; con otro cliente responde
  `403 NOT_TICKET_OWNER`.
- `size=101` responde `400 VALIDATION_ERROR`.

## 7. Hallazgos del E2E

La primera corrida completa (6/11/2026) encontró que **todos** los
`GET /api/catalog/events` devolvían `500` por parámetros opcionales nulos en la consulta JPQL
(`lower(bytea) does not exist`). Se corrigió en el PR
[#10](https://github.com/Francogp131/TicketYa/pull/10), con un test de regresión contra
PostgreSQL real (`EventoBusquedaIntegrationTest`). La colección volvió a correr completa:
45/45 requests y 45/45 assertions.

Evidencia de la suite: [`docs/evidencia/dia-4-suite.txt`](evidencia/dia-4-suite.txt).

## 8. OpenAPI

```bash
curl -s http://localhost:8081/v3/api-docs | jq '.paths | keys'
```

Debe listar los nueve endpoints de catálogo, incluidos `GET /api/catalog/events`,
`POST /api/catalog/events/{eventoId}/cancel` y `PATCH /internal/events/{eventoId}/availability`.
Swagger UI: <http://localhost:8081/swagger-ui.html>.
