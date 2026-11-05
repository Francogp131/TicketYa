package ar.edu.unicen.ticketya.inventory.servicio;

import ar.edu.unicen.ticketya.inventory.web.ApiException;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * El advisory lock se sostiene hasta el commit de la reserva.
 * Las entradas vendidas salen de {@code cliente_evento}; al confirmar (tarea 3.6)
 * hay que sumarlas ahí, porque la reserva deja de estar {@code VIGENTE}.
 */
@Service
public class LimitePorCliente {

    static final String LOCK = "SELECT pg_advisory_xact_lock(hashtext(? || ?))";

    static final String CONTAR = """
            SELECT COALESCE((
                       SELECT entradas_vendidas
                         FROM cliente_evento
                        WHERE evento_id = ? AND cliente_id = ?
                   ), 0)
                 + COALESCE((
                       SELECT SUM(ri.cantidad)::int
                         FROM reserva r
                         JOIN reserva_item ri ON ri.reserva_id = r.id
                        WHERE r.evento_id = ? AND r.cliente_id = ?
                          AND r.estado = 'VIGENTE'
                   ), 0)
            """;

    private final JdbcTemplate jdbcTemplate;

    public LimitePorCliente(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void verificar(UUID eventoId, UUID clienteId, int limite, int cantidadPedida) {
        jdbcTemplate.query(LOCK, rs -> {
            rs.next();
            return null;
        }, eventoId.toString(), clienteId.toString());
        Number ocupadas = jdbcTemplate.queryForObject(CONTAR, Number.class,
                eventoId, clienteId, eventoId, clienteId);
        int yaTomadas = ocupadas == null ? 0 : ocupadas.intValue();
        if (yaTomadas + cantidadPedida > limite) {
            throw ApiException.unprocessable("CLIENT_LIMIT_EXCEEDED",
                    "El cliente supera el límite de " + limite + " entradas para este evento");
        }
    }
}
