package ar.edu.unicen.ticketya.inventory.servicio;

import ar.edu.unicen.ticketya.inventory.dominio.EstadoReserva;
import ar.edu.unicen.ticketya.inventory.dominio.Reserva;
import ar.edu.unicen.ticketya.inventory.repositorio.ReservaRepository;
import ar.edu.unicen.ticketya.inventory.web.ApiException;
import ar.edu.unicen.ticketya.inventory.web.dto.ReservationResponse;
import ar.edu.unicen.ticketya.inventory.web.dto.ReturnRequest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ConfirmacionService {

    private static final String CONFIRMAR = """
            UPDATE reserva
               SET estado = 'CONFIRMADA', updated_at = now()
             WHERE id = ? AND estado = 'VIGENTE' AND expires_at > ?
            """;

    private static final String VENDER_ASIENTOS = """
            UPDATE asiento_inventario
               SET estado = 'VENDIDO'
             WHERE reserva_id = ? AND estado = 'RESERVADO'
            """;

    private static final String SUMAR_VENDIDAS = """
            INSERT INTO cliente_evento (evento_id, cliente_id, entradas_vendidas, updated_at)
            VALUES (?, ?, ?, now())
            ON CONFLICT (evento_id, cliente_id)
            DO UPDATE SET entradas_vendidas = cliente_evento.entradas_vendidas + EXCLUDED.entradas_vendidas,
                          updated_at = now()
            """;

    private static final String LIBERAR_RESERVA = """
            UPDATE reserva
               SET estado = 'LIBERADA', updated_at = now()
             WHERE id = ? AND estado = 'VIGENTE'
            """;

    private static final String VENCER_RESERVA = """
            UPDATE reserva
               SET estado = 'VENCIDA', updated_at = now()
             WHERE id = ? AND estado = 'VIGENTE' AND expires_at <= ?
            """;

    private static final String LIBERAR_ASIENTOS = """
            UPDATE asiento_inventario
               SET estado = 'LIBRE', reserva_id = NULL, expires_at = NULL
             WHERE reserva_id = ? AND estado = 'RESERVADO'
            """;

    private static final String REPONER_ZONA = """
            UPDATE sector_inventario s
               SET disponibles = LEAST(s.capacidad, s.disponibles + t.cantidad)
              FROM (
                    SELECT sector_id, SUM(cantidad)::int AS cantidad
                      FROM reserva_item
                     WHERE reserva_id = ? AND asiento_id IS NULL
                     GROUP BY sector_id
                   ) t
             WHERE s.evento_id = ? AND s.sector_id = t.sector_id
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ReservaRepository reservaRepository;
    private final ReservaService reservaService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public ConfirmacionService(JdbcTemplate jdbcTemplate,
                               ReservaRepository reservaRepository,
                               ReservaService reservaService,
                               TransactionTemplate transactionTemplate,
                               Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.reservaRepository = reservaRepository;
        this.reservaService = reservaService;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    public ReservationResponse confirmar(UUID reservaId) {
        Paso paso = transactionTemplate.execute(status -> aplicarConfirmacion(reservaId));
        if (paso == Paso.CONFIRMADA) {
            return reservaService.obtenerInterno(reservaId);
        }
        if (paso == Paso.VENCIDA) {
            throw ApiException.gone("RESERVATION_EXPIRED", "La reserva está vencida y no se puede pagar");
        }
        throw ApiException.conflict("RESERVATION_NOT_CONFIRMABLE", "La reserva no se puede confirmar");
    }

    public void liberar(UUID reservaId) {
        transactionTemplate.executeWithoutResult(status -> aplicarLiberacion(reservaId));
    }

    public void devolver(String clave, ReturnRequest request) {
        transactionTemplate.executeWithoutResult(status -> aplicarDevolucion(clave, request));
    }

    private Paso aplicarConfirmacion(UUID reservaId) {
        OffsetDateTime ahora = OffsetDateTime.now(clock);
        int confirmadas = jdbcTemplate.update(CONFIRMAR, reservaId, ahora);
        if (confirmadas == 1) {
            jdbcTemplate.update(VENDER_ASIENTOS, reservaId);
            sumarVendidas(reservaId);
            return Paso.CONFIRMADA;
        }
        Reserva reserva = buscar(reservaId);
        if (reserva.getEstado() == EstadoReserva.CONFIRMADA) {
            return Paso.CONFIRMADA;
        }
        if (reserva.getEstado() == EstadoReserva.VIGENTE && !reserva.getExpiresAt().isAfter(ahora)) {
            jdbcTemplate.update(VENCER_RESERVA, reservaId, ahora);
            liberarLugares(reservaId, reserva.getEventoId());
            return Paso.VENCIDA;
        }
        return Paso.NO_CONFIRMABLE;
    }

    private void aplicarLiberacion(UUID reservaId) {
        Reserva reserva = buscar(reservaId);
        int liberadas = jdbcTemplate.update(LIBERAR_RESERVA, reservaId);
        if (liberadas == 1) {
            liberarLugares(reservaId, reserva.getEventoId());
            return;
        }
        if (reserva.getEstado() == EstadoReserva.LIBERADA || reserva.getEstado() == EstadoReserva.VENCIDA) {
            return;
        }
        throw ApiException.conflict("RESERVATION_NOT_RELEASABLE", "La reserva confirmada no se libera: hay que devolverla");
    }

    private void aplicarDevolucion(String clave, ReturnRequest request) {
        if (clave != null && !clave.isBlank()) {
            int insertadas = jdbcTemplate.update("""
                    INSERT INTO devolucion_aplicada (clave, evento_id)
                    VALUES (?, ?)
                    ON CONFLICT (clave) DO NOTHING
                    """, clave, request.eventoId());
            if (insertadas == 0) {
                return;
            }
        }
        for (ReturnRequest.Item item : request.items()) {
            if (item.asientoId() == null) {
                reponerZona(request.eventoId(), item);
            } else {
                devolverAsiento(request.eventoId(), item.asientoId());
            }
        }
    }

    private void devolverAsiento(UUID eventoId, UUID asientoId) {
        List<UUID> clientes = jdbcTemplate.query("""
                WITH objetivo AS (
                    SELECT a.id, r.cliente_id
                      FROM asiento_inventario a
                      JOIN reserva r ON r.id = a.reserva_id
                     WHERE a.evento_id = ? AND a.asiento_id = ? AND a.estado = 'VENDIDO'
                     FOR UPDATE OF a
                )
                UPDATE asiento_inventario a
                   SET estado = 'LIBRE', reserva_id = NULL, expires_at = NULL
                  FROM objetivo o
                 WHERE a.id = o.id
                RETURNING o.cliente_id
                """, (rs, rowNum) -> rs.getObject("cliente_id", UUID.class), eventoId, asientoId);
        for (UUID clienteId : clientes) {
            jdbcTemplate.update("""
                    UPDATE cliente_evento
                       SET entradas_vendidas = GREATEST(0, entradas_vendidas - 1), updated_at = now()
                     WHERE evento_id = ? AND cliente_id = ?
                    """, eventoId, clienteId);
        }
    }

    private void reponerZona(UUID eventoId, ReturnRequest.Item item) {
        int actualizadas = jdbcTemplate.update("""
                UPDATE sector_inventario
                   SET disponibles = LEAST(capacidad, disponibles + ?)
                 WHERE evento_id = ? AND sector_id = ? AND tipo = 'NO_NUMERADO'
                """, item.cantidad(), eventoId, item.sectorId());
        if (actualizadas == 0) {
            throw ApiException.notFound("SECTOR_NOT_FOUND", "No existe la zona " + item.sectorId());
        }
    }

    private void sumarVendidas(UUID reservaId) {
        Integer cantidad = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(cantidad), 0)::int FROM reserva_item WHERE reserva_id = ?",
                Integer.class, reservaId);
        UUID eventoId = jdbcTemplate.queryForObject(
                "SELECT evento_id FROM reserva WHERE id = ?", UUID.class, reservaId);
        UUID clienteId = jdbcTemplate.queryForObject(
                "SELECT cliente_id FROM reserva WHERE id = ?", UUID.class, reservaId);
        jdbcTemplate.update(SUMAR_VENDIDAS, eventoId, clienteId, cantidad == null ? 0 : cantidad);
    }

    private void liberarLugares(UUID reservaId, UUID eventoId) {
        jdbcTemplate.update(LIBERAR_ASIENTOS, reservaId);
        jdbcTemplate.update(REPONER_ZONA, reservaId, eventoId);
    }

    private Reserva buscar(UUID reservaId) {
        return reservaRepository.findById(reservaId)
                .orElseThrow(() -> ApiException.notFound("RESERVATION_NOT_FOUND",
                        "No existe la reserva " + reservaId));
    }

    private enum Paso {
        CONFIRMADA,
        VENCIDA,
        NO_CONFIRMABLE
    }
}
