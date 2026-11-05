# Pruebas — TicketYa Entrega 1

Este documento reúne la estrategia, los comandos y la evidencia de las pruebas de los Días 2 y 3.
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
# Catálogo (incluye integración Testcontainers de sectores y asientos)
cd catalog-service && mvn test

# Inventario (unitarios + concurrencia Testcontainers)
cd inventory-service && mvn test

# Órdenes (unitarios + idempotencia concurrente Testcontainers)
cd orders-service && mvn test

# Gateway
cd api-gateway && mvn test
```

Los tests con Testcontainers se omiten automáticamente si Docker no está disponible
(`@Testcontainers(disabledWithoutDocker = true)`), salvo en CI, donde el runner lo tiene.

## 3. Evidencia de la suite

Resultado de la última corrida local (5/11/2026):

| Proyecto | Tests | Resultado |
|---|---|---|
| catalog-service | 47 | 0 fallos, 0 errores, 0 omitidos |
| inventory-service | 23 | 0 fallos, 0 errores, 0 omitidos |
| orders-service | 23 | 0 fallos, 0 errores, 0 omitidos |
| api-gateway | 5 | 0 fallos, 0 errores, 0 omitidos |

Desglose relevante:

- `EventServiceTest` (23): estados, cancelación con/sin orders, callback de disponibilidad,
  scheduler de finalización, búsqueda por fecha/precio/relevancia, rango y orden inválidos.
- `ReservaServiceTest` (16): asientos, zonas, límite por cliente, vencimiento, confirmación,
  liberación y devoluciones idempotentes.
- `ReservaConcurrencyIntegrationTest` (2): 100 hilos sobre el mismo asiento y 40 hilos sobre
  el último lugar de una zona; en ambos casos exactamente un ganador.
- `OrderServiceTest` (13): pago aprobado, rechazado, timeout, compensación por confirmación
  y por emisión de entradas, idempotencia y errores de reserva.
- `IdempotencyServiceTest` (5): misma respuesta, 422 con cuerpo distinto, 409 en proceso,
  hash determinista.
- `OrderIdempotencyIntegrationTest` (2): dos reintentos concurrentes crean una sola orden y
  el reintento secuencial devuelve la misma orden y las mismas entradas.

## 4. Prueba de concurrencia (tarea 3.7)

```bash
cd inventory-service
mvn -Dtest=ReservaConcurrencyIntegrationTest test
```

La prueba levanta PostgreSQL 16 con Testcontainers, crea un evento y:

1. Lanza 100 hilos que intentan reservar el mismo asiento. Espera **1 éxito** y el resto
   `SEAT_NOT_AVAILABLE`; la base debe tener **una** reserva y el asiento `RESERVADO`.
2. Lanza 40 hilos sobre una zona con un solo lugar. Espera **1 éxito**, el resto
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

Resultado de la última corrida (5/11/2026): **34 requests, 34 assertions, 0 fallos**.
Incluye catálogo, búsqueda, publicación, reserva numerada y de zona, compra aprobada con QR,
reintento idempotente, reuso de clave, pago rechazado, pago lento y cancelación.

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
CLIENTE=2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e
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
evento pasan a `EN_REINTEGRO` en `orders_db`. Repetir el endpoint interno devuelve la misma
cantidad de entradas afectadas.

## 7. OpenAPI

```bash
curl -s http://localhost:8081/v3/api-docs | jq '.paths | keys'
```

Debe listar los nueve endpoints de catálogo, incluidos `GET /api/catalog/events`,
`POST /api/catalog/events/{eventoId}/cancel` y `PATCH /internal/events/{eventoId}/availability`.
Swagger UI: <http://localhost:8081/swagger-ui.html>.
