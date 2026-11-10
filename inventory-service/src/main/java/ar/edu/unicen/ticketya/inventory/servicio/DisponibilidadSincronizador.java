package ar.edu.unicen.ticketya.inventory.servicio;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Actualiza el estado local a AGOTADO o PUBLICADO y, solo si cambió, avisa a catalog
 * después del commit. Un fallo de catalog no deshace la reserva.
 */
@Service
public class DisponibilidadSincronizador {

    private static final Object RECURSO = new Object();

    private final JdbcTemplate jdbcTemplate;
    private final CatalogClient catalogClient;
    private final Clock clock;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public DisponibilidadSincronizador(JdbcTemplate jdbcTemplate, CatalogClient catalogClient, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.catalogClient = catalogClient;
        this.clock = clock;
    }

    public void sincronizar(UUID eventoId) {
        Boolean hayLugar = jdbcTemplate.queryForObject("""
                SELECT (
                    EXISTS (
                        SELECT 1 FROM sector_inventario
                         WHERE evento_id = ? AND tipo = 'NO_NUMERADO' AND COALESCE(disponibles, 0) > 0
                    )
                    OR EXISTS (
                        SELECT 1 FROM asiento_inventario
                         WHERE evento_id = ? AND estado = 'LIBRE'
                    )
                )
                """, Boolean.class, eventoId, eventoId);
        boolean agotado = !Boolean.TRUE.equals(hayLugar);
        String nuevo = agotado ? "AGOTADO" : "PUBLICADO";
        int cambiados = jdbcTemplate.update("""
                UPDATE evento_inventario
                   SET estado = ?, updated_at = ?
                 WHERE evento_id = ? AND estado IN ('PUBLICADO', 'AGOTADO') AND estado <> ?
                """, nuevo, OffsetDateTime.now(clock), eventoId, nuevo);
        if (cambiados == 1) {
            registrarAviso(eventoId, agotado);
        }
    }

    private void registrarAviso(UUID eventoId, boolean agotado) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            executor.execute(() -> catalogClient.notificarDisponibilidad(eventoId, agotado));
            return;
        }
        Map<UUID, Boolean> avisos = avisosDeLaTransaccion();
        avisos.put(eventoId, agotado);
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, Boolean> avisosDeLaTransaccion() {
        Map<UUID, Boolean> avisos = (Map<UUID, Boolean>) TransactionSynchronizationManager.getResource(RECURSO);
        if (avisos != null) {
            return avisos;
        }
        avisos = new LinkedHashMap<>();
        TransactionSynchronizationManager.bindResource(RECURSO, avisos);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                Map<UUID, Boolean> pendientes =
                        (Map<UUID, Boolean>) TransactionSynchronizationManager.getResource(RECURSO);
                if (pendientes == null) {
                    return;
                }
                pendientes.forEach((id, flag) -> executor.execute(() -> catalogClient.notificarDisponibilidad(id, flag)));
            }

            @Override
            public void afterCompletion(int status) {
                if (TransactionSynchronizationManager.hasResource(RECURSO)) {
                    TransactionSynchronizationManager.unbindResource(RECURSO);
                }
            }
        });
        return avisos;
    }
}
