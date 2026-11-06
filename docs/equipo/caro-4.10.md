# Caro — tarea 4.10: consultas propias (guía exacta)

**Contexto:** tu PR #2 (test de orden inexistente → 404) ya está mergeado y el CI quedó verde.
No hay nada que corregir de eso. Para el Día 4 quedan 4.8, 4.9, 4.10 y 4.11; P5 ya dejó
4.8/4.9 en revisión y vos hacés **4.10**, que es autocontenida.

**Rama:**

```bash
git switch main
git pull origin main
git switch -c feat/orders-consultas
```

## 1. Qué tenés que lograr

Tres endpoints, todos con identidad por headers (`X-User-Id`, `X-User-Role`) y solo con los
datos del cliente autenticado:

| Método | Ruta | Respuesta |
|---|---|---|
| GET | `/api/orders?page=0&size=20` | `PageResponse<OrderResponse>` del cliente |
| GET | `/api/orders/tickets?eventoId={uuid}&page=0&size=20` | `PageResponse<TicketResponse>` del cliente |
| GET | `/api/orders/tickets/{entradaId}` | `TicketResponse`; 403 si es ajena, 404 si no existe |

Reglas:

- `page >= 0`, `1 <= size <= 100`; fuera de rango → `400 VALIDATION_ERROR`.
- Orden: más recientes primero (`created_at DESC`).
- Orden ajena → `403 ORDER_NOT_OWNER` (ya existe en `OrderService.obtener`).
- Entrada ajena → `403 NOT_TICKET_OWNER`; inexistente → `404 TICKET_NOT_FOUND`.
- Un `ADMIN` no ve órdenes de otros: la consulta siempre filtra por `X-User-Id`.

## 2. Archivos que tocás

### 2.1 DTOs nuevos

`orders-service/src/main/java/ar/edu/unicen/ticketya/orders/web/dto/PageResponse.java`

```java
package ar.edu.unicen.ticketya.orders.web.dto;

import java.util.List;

public record PageResponse<T>(List<T> content, int page, int size,
                              long totalElements, int totalPages) {
}
```

`.../web/dto/TicketResponse.java`

```java
package ar.edu.unicen.ticketya.orders.web.dto;

import ar.edu.unicen.ticketya.orders.dominio.Entrada;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TicketResponse(
        UUID id,
        UUID eventoId,
        String eventoNombre,
        OffsetDateTime eventoFecha,
        UUID sectorId,
        UUID asientoId,
        UUID qrCode,
        String estado,
        OffsetDateTime createdAt) {

    public static TicketResponse from(Entrada e) {
        return new TicketResponse(e.getId(), e.getEventoId(), e.getEventoNombre(),
                e.getEventoFecha(), e.getSectorId(), e.getAsientoId(), e.getQrCode(),
                e.getEstado().name(), e.getCreatedAt());
    }
}
```

### 2.2 Repositorio

`.../repositorio/EntradaRepository.java` — agregá:

```java
Page<Entrada> findByClienteIdOrderByCreatedAtDesc(UUID clienteId, Pageable pageable);

Page<Entrada> findByClienteIdAndEventoIdOrderByCreatedAtDesc(
        UUID clienteId, UUID eventoId, Pageable pageable);
```

Importá `org.springframework.data.domain.Page` y `Pageable`.

`OrdenRepository` ya tiene `findByClienteIdOrderByCreatedAtDesc`; no lo toques.

### 2.3 Servicio nuevo

`.../servicio/OrderQueryService.java`

```java
@Service
public class OrderQueryService {

    private final OrdenRepository ordenRepository;
    private final OrdenItemRepository ordenItemRepository;
    private final EntradaRepository entradaRepository;

    // constructor con los tres

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> ordenes(UUID clienteId, int page, int size) {
        validarPagina(page, size);
        Page<Orden> ordenes = ordenRepository.findByClienteIdOrderByCreatedAtDesc(
                clienteId, PageRequest.of(page, size));
        List<OrderResponse> content = ordenes.getContent().stream()
                .map(orden -> OrderResponse.from(orden,
                        ordenItemRepository.findByOrdenId(orden.getId()),
                        entradaRepository.findByOrdenId(orden.getId())))
                .toList();
        return new PageResponse<>(content, page, size,
                ordenes.getTotalElements(), ordenes.getTotalPages());
    }

    @Transactional(readOnly = true)
    public PageResponse<TicketResponse> entradas(UUID clienteId, UUID eventoId,
                                                  int page, int size) {
        validarPagina(page, size);
        Page<Entrada> entradas = eventoId == null
                ? entradaRepository.findByClienteIdOrderByCreatedAtDesc(
                        clienteId, PageRequest.of(page, size))
                : entradaRepository.findByClienteIdAndEventoIdOrderByCreatedAtDesc(
                        clienteId, eventoId, PageRequest.of(page, size));
        return new PageResponse<>(entradas.getContent().stream()
                .map(TicketResponse::from).toList(),
                page, size, entradas.getTotalElements(), entradas.getTotalPages());
    }

    @Transactional(readOnly = true)
    public TicketResponse entrada(UUID entradaId, UUID clienteId) {
        Entrada entrada = entradaRepository.findById(entradaId)
                .orElseThrow(() -> ApiException.notFound("TICKET_NOT_FOUND",
                        "No existe la entrada " + entradaId));
        if (!entrada.getClienteId().equals(clienteId)) {
            throw ApiException.forbidden("NOT_TICKET_OWNER",
                    "La entrada pertenece a otro cliente");
        }
        return TicketResponse.from(entrada);
    }

    private void validarPagina(int page, int size) {
        if (page < 0) {
            throw ApiException.badRequest("VALIDATION_ERROR", "page debe ser >= 0");
        }
        if (size < 1 || size > 100) {
            throw ApiException.badRequest("VALIDATION_ERROR", "size debe estar entre 1 y 100");
        }
    }
}
```

> `OrderResponse` ya existe. Si no tiene un `from(...)` estático, agregalo con los mismos
> campos que usa `OrderService.toResponse` (no dupliques lógica: movelo a `OrderResponse` o
> hacé el mapeo en el servicio).

### 2.4 Controllers

En `OrderController` agregá:

```java
@GetMapping
public PageResponse<OrderResponse> listar(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestHeader(value = "X-User-Id", required = false) String userId,
        @RequestHeader(value = "X-User-Role", required = false) String role) {
    RequestIdentity identity = RequestIdentity.require(userId, role);
    return orderQueryService.ordenes(identity.userId(), page, size);
}
```

Creá `TicketController` (`@RequestMapping("/api/orders/tickets")`):

```java
@GetMapping
public PageResponse<TicketResponse> listar(
        @RequestParam(required = false) UUID eventoId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestHeader(...) String userId, @RequestHeader(...) String role) {
    RequestIdentity identity = RequestIdentity.require(userId, role);
    return orderQueryService.entradas(identity.userId(), eventoId, page, size);
}

@GetMapping("/{entradaId}")
public TicketResponse obtener(@PathVariable UUID entradaId,
        @RequestHeader(...) String userId, @RequestHeader(...) String role) {
    RequestIdentity identity = RequestIdentity.require(userId, role);
    return orderQueryService.entrada(entradaId, identity.userId());
}
```

## 3. Tests que tenés que escribir

`OrderQueryServiceTest` (Mockito, sin Docker):

1. `ordenesDevuelveSoloLasDelClienteYOrdenaPorFecha` — mockeá `findByClienteId...` y
   verificá que se llama con el `clienteId` correcto y que el `PageResponse` conserva
   `totalElements`/`totalPages`.
2. `entradasFiltraPorEventoCuandoSeEnviaEventoId` — verificá que usa el método con
   `eventoId`.
3. `entradaAjenaDevuelve403` — `ApiException` con `NOT_TICKET_OWNER`.
4. `entradaInexistenteDevuelve404` — `TICKET_NOT_FOUND`.
5. `paginaInvalidaDevuelve400` — `page=-1` y `size=101`.

Opcional pero recomendado: `TicketControllerTest` con `@WebMvcTest` para 403/404 de identidad.

## 4. Comandos

```bash
cd orders-service
mvn clean test
mvn spring-boot:run
```

Probar por gateway con los headers del seed:

```bash
CLIENTE=33333333-3333-4333-8333-333333333333
curl "http://localhost:8080/api/orders?page=0&size=5" \
  -H "X-User-Id: $CLIENTE" -H "X-User-Role: CLIENT"
curl "http://localhost:8080/api/orders/tickets?page=0&size=5" \
  -H "X-User-Id: $CLIENTE" -H "X-User-Role: CLIENT"
```

## 5. PR

- Rama: `feat/orders-consultas`.
- Título: `feat(orders): consultas propias de órdenes y entradas (tarea 4.10)`.
- Marcá **solo 4.10** en `openspec/changes/entrega-1-core/tasks.md`.
- En el cuerpo pegá `mvn clean test` y los dos `curl` con su respuesta.
- No toques 4.8/4.9: ya están en el PR de devoluciones.
- No toques catalog ni inventory.
