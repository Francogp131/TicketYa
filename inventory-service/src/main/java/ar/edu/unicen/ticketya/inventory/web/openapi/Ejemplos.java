package ar.edu.unicen.ticketya.inventory.web.openapi;

/**
 * Ejemplos del seed de inventory (Festival de Rock Tandil 2027) para el OpenAPI.
 */
public final class Ejemplos {

    public static final String EVENTO = "d0000000-0000-4000-8000-000000000001";
    public static final String PLATEA = "b0000000-0000-4000-8000-000000000001";
    public static final String CAMPO = "b0000000-0000-4000-8000-000000000002";
    public static final String ASIENTO = "c0000000-0000-4000-8000-010101000000";
    public static final String CLIENTE = "2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e";
    public static final String RESERVA = "b1c2d3e4-f5a6-4b7c-8d9e-0f1a2b3c4d5e";

    public static final String DISPONIBILIDAD = """
            {
              "eventoId": "d0000000-0000-4000-8000-000000000001",
              "estado": "PUBLICADO",
              "sectores": [
                {
                  "sectorId": "b0000000-0000-4000-8000-000000000002",
                  "nombre": "Campo",
                  "tipo": "NO_NUMERADO",
                  "precio": 18000.00,
                  "capacidad": 100,
                  "disponibles": 100
                },
                {
                  "sectorId": "b0000000-0000-4000-8000-000000000001",
                  "nombre": "Platea",
                  "tipo": "NUMERADO",
                  "precio": 25000.00,
                  "capacidad": 20,
                  "disponibles": 20
                }
              ]
            }
            """;

    public static final String ASIENTOS = """
            [
              { "asientoId": "c0000000-0000-4000-8000-010101000000", "fila": 1, "numero": 1, "estado": "LIBRE" },
              { "asientoId": "c0000000-0000-4000-8000-010102000000", "fila": 1, "numero": 2, "estado": "RESERVADO" }
            ]
            """;

    public static final String RESERVA_PEDIDO = """
            {
              "eventoId": "d0000000-0000-4000-8000-000000000001",
              "items": [
                {
                  "sectorId": "b0000000-0000-4000-8000-000000000001",
                  "asientoIds": ["c0000000-0000-4000-8000-010101000000"]
                }
              ]
            }
            """;

    public static final String RESERVA_CREADA = """
            {
              "id": "b1c2d3e4-f5a6-4b7c-8d9e-0f1a2b3c4d5e",
              "eventoId": "d0000000-0000-4000-8000-000000000001",
              "eventoNombre": "Festival de Rock Tandil 2027",
              "eventoFecha": "2027-03-14T21:00:00-03:00",
              "clienteId": "2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e",
              "estado": "VIGENTE",
              "expiresAt": "2026-11-05T19:10:00-03:00",
              "items": [
                {
                  "sectorId": "b0000000-0000-4000-8000-000000000001",
                  "asientoId": "c0000000-0000-4000-8000-010101000000",
                  "cantidad": 1,
                  "precioUnitario": 25000.00,
                  "subtotal": 25000.00
                }
              ],
              "total": 25000.00
            }
            """;

    public static final String ALTA_INVENTARIO = """
            {
              "eventoId": "d0000000-0000-4000-8000-000000000001",
              "nombre": "Festival de Rock Tandil 2027",
              "fecha": "2027-03-14T21:00:00-03:00",
              "limitePorCliente": 4,
              "version": 1,
              "sectores": [
                {
                  "sectorId": "b0000000-0000-4000-8000-000000000001",
                  "nombre": "Platea",
                  "tipo": "NUMERADO",
                  "precio": 25000.00,
                  "capacidad": 20,
                  "filas": 4,
                  "asientos": [
                    { "asientoId": "c0000000-0000-4000-8000-010101000000", "fila": 1, "numero": 1 }
                  ]
                }
              ]
            }
            """;

    public static final String ALTA_RESPUESTA = """
            {
              "eventoId": "d0000000-0000-4000-8000-000000000001",
              "sectores": 2,
              "asientos": 20,
              "idempotente": true
            }
            """;

    public static final String LIBERAR = """
            { "motivo": "PAGO_RECHAZADO" }
            """;

    public static final String DEVOLVER = """
            {
              "eventoId": "d0000000-0000-4000-8000-000000000001",
              "items": [
                {
                  "sectorId": "b0000000-0000-4000-8000-000000000001",
                  "asientoId": "c0000000-0000-4000-8000-010101000000",
                  "cantidad": 1
                }
              ]
            }
            """;

    public static final String VALIDACION = """
            {
              "type": "https://ticketya.local/errors/validation-error",
              "title": "Bad Request",
              "status": 400,
              "detail": "La solicitud tiene campos inválidos",
              "instance": "/api/inventory/reservations",
              "code": "VALIDATION_ERROR",
              "errors": [
                { "field": "items", "message": "un ítem numerado requiere asientoIds y no admite cantidad" }
              ]
            }
            """;

    public static final String NO_AUTENTICADO = """
            {
              "type": "https://ticketya.local/errors/unauthorized",
              "title": "Unauthorized",
              "status": 401,
              "detail": "Faltan los encabezados X-User-Id y X-User-Role",
              "instance": "/api/inventory/reservations",
              "code": "UNAUTHORIZED",
              "errors": []
            }
            """;

    public static final String AJENA = """
            {
              "type": "https://ticketya.local/errors/not-reservation-owner",
              "title": "Forbidden",
              "status": 403,
              "detail": "La reserva pertenece a otro cliente",
              "instance": "/api/inventory/reservations/b1c2d3e4-f5a6-4b7c-8d9e-0f1a2b3c4d5e",
              "code": "NOT_RESERVATION_OWNER",
              "errors": []
            }
            """;

    public static final String EVENTO_NO_ENCONTRADO = """
            {
              "type": "https://ticketya.local/errors/event-not-found",
              "title": "Not Found",
              "status": 404,
              "detail": "No existe el evento d0000000-0000-4000-8000-000000000001",
              "instance": "/api/inventory/events/d0000000-0000-4000-8000-000000000001/availability",
              "code": "EVENT_NOT_FOUND",
              "errors": []
            }
            """;

    public static final String SECTOR_NO_ENCONTRADO = """
            {
              "type": "https://ticketya.local/errors/sector-not-found",
              "title": "Not Found",
              "status": 404,
              "detail": "No existe el sector b0000000-0000-4000-8000-000000000001 en el evento",
              "instance": "/api/inventory/events/d0000000-0000-4000-8000-000000000001/seats",
              "code": "SECTOR_NOT_FOUND",
              "errors": []
            }
            """;

    public static final String RESERVA_NO_ENCONTRADA = """
            {
              "type": "https://ticketya.local/errors/reservation-not-found",
              "title": "Not Found",
              "status": 404,
              "detail": "No existe la reserva b1c2d3e4-f5a6-4b7c-8d9e-0f1a2b3c4d5e",
              "instance": "/api/inventory/reservations/b1c2d3e4-f5a6-4b7c-8d9e-0f1a2b3c4d5e",
              "code": "RESERVATION_NOT_FOUND",
              "errors": []
            }
            """;

    public static final String ASIENTO_OCUPADO = """
            {
              "type": "https://ticketya.local/errors/seat-not-available",
              "title": "Conflict",
              "status": 409,
              "detail": "El asiento c0000000-0000-4000-8000-010101000000 ya no está disponible",
              "instance": "/api/inventory/reservations",
              "code": "SEAT_NOT_AVAILABLE",
              "errors": []
            }
            """;

    public static final String ZONA_SIN_LUGAR = """
            {
              "type": "https://ticketya.local/errors/insufficient-availability",
              "title": "Conflict",
              "status": 409,
              "detail": "No hay disponibilidad suficiente en la zona b0000000-0000-4000-8000-000000000002",
              "instance": "/api/inventory/reservations",
              "code": "INSUFFICIENT_AVAILABILITY",
              "errors": []
            }
            """;

    public static final String EVENTO_NO_EN_VENTA = """
            {
              "type": "https://ticketya.local/errors/event-not-on-sale",
              "title": "Conflict",
              "status": 409,
              "detail": "El evento no está en venta",
              "instance": "/api/inventory/reservations",
              "code": "EVENT_NOT_ON_SALE",
              "errors": []
            }
            """;

    public static final String LIMITE = """
            {
              "type": "https://ticketya.local/errors/client-limit-exceeded",
              "title": "Unprocessable Entity",
              "status": 422,
              "detail": "El pedido supera el límite de entradas del cliente para este evento",
              "instance": "/api/inventory/reservations",
              "code": "CLIENT_LIMIT_EXCEEDED",
              "errors": []
            }
            """;

    public static final String RESERVA_VENCIDA = """
            {
              "type": "https://ticketya.local/errors/reservation-expired",
              "title": "Gone",
              "status": 410,
              "detail": "La reserva está vencida y no se puede pagar",
              "instance": "/internal/reservations/b1c2d3e4-f5a6-4b7c-8d9e-0f1a2b3c4d5e/confirm",
              "code": "RESERVATION_EXPIRED",
              "errors": []
            }
            """;

    private Ejemplos() {
    }
}
