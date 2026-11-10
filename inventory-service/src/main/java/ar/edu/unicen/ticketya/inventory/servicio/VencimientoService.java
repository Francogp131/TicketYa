package ar.edu.unicen.ticketya.inventory.servicio;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pasa a VENCIDA las reservas cuyo plazo ya cumplió y devuelve sus lugares.
 * El update de la reserva es el que gana: si otra instancia ya la venció, no se libera dos veces.
 */
@Service
public class VencimientoService {

    static final String MARCAR_VENCIDAS = """
            UPDATE reserva
               SET estado = 'VENCIDA', updated_at = now()
             WHERE estado = 'VIGENTE' AND expires_at <= ?
            RETURNING id
            """;

    static final String MARCAR_UNA = """
            UPDATE reserva
               SET estado = 'VENCIDA', updated_at = now()
             WHERE id = ? AND estado = 'VIGENTE' AND expires_at <= ?
            RETURNING id
            """;

    static final String LIBERAR_ASIENTOS = """
            UPDATE asiento_inventario
               SET estado = 'LIBRE', reserva_id = NULL, expires_at = NULL
             WHERE reserva_id = ? AND estado = 'RESERVADO'
            """;

    static final String REPONER_ZONA = """
            UPDATE sector_inventario s
               SET disponibles = s.disponibles + t.cantidad
              FROM (
                    SELECT sector_id, SUM(cantidad)::int AS cantidad
                      FROM reserva_item
                     WHERE reserva_id = ? AND asiento_id IS NULL
                     GROUP BY sector_id
                   ) t
             WHERE s.evento_id = ? AND s.sector_id = t.sector_id
            """;

    private final JdbcTemplate jdbcTemplate;
    private final DisponibilidadSincronizador disponibilidadSincronizador;
    private final Clock clock;

    public VencimientoService(JdbcTemplate jdbcTemplate,
                              DisponibilidadSincronizador disponibilidadSincronizador,
                              Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.disponibilidadSincronizador = disponibilidadSincronizador;
        this.clock = clock;
    }

    @Transactional
    public int liberarVencidas() {
        List<UUID> vencidas = jdbcTemplate.query(MARCAR_VENCIDAS,
                (rs, rowNum) -> rs.getObject("id", UUID.class), OffsetDateTime.now(clock));
        vencidas.forEach(this::liberarItems);
        return vencidas.size();
    }

    /**
     * @return true si esta llamada pasó la reserva de VIGENTE a VENCIDA
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean liberarSiVencio(UUID reservaId) {
        List<UUID> vencidas = jdbcTemplate.query(MARCAR_UNA,
                (rs, rowNum) -> rs.getObject("id", UUID.class), reservaId, OffsetDateTime.now(clock));
        if (vencidas.isEmpty()) {
            return false;
        }
        liberarItems(reservaId);
        return true;
    }

    private void liberarItems(UUID reservaId) {
        UUID eventoId = jdbcTemplate.queryForObject(
                "SELECT evento_id FROM reserva WHERE id = ?", UUID.class, reservaId);
        jdbcTemplate.update(LIBERAR_ASIENTOS, reservaId);
        jdbcTemplate.update(REPONER_ZONA, reservaId, eventoId);
        disponibilidadSincronizador.sincronizar(eventoId);
    }
}
