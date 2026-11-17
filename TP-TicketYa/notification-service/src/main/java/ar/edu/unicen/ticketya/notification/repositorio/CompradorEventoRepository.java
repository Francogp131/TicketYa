package ar.edu.unicen.ticketya.notification.repositorio;

import ar.edu.unicen.ticketya.notification.dominio.CompradorEvento;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompradorEventoRepository extends JpaRepository<CompradorEvento, UUID> {

    Optional<CompradorEvento> findByEventoIdAndClienteId(UUID eventoId, UUID clienteId);

    List<CompradorEvento> findByEventoIdOrderByClienteIdAsc(UUID eventoId);

    @Modifying
    @Query(value = """
            INSERT INTO comprador_evento
                (id, evento_id, cliente_id, cliente_email, evento_nombre, evento_fecha, evento_ciudad, updated_at)
            VALUES
                (:id, :eventoId, :clienteId, :clienteEmail, :eventoNombre, :eventoFecha, :eventoCiudad, now())
            ON CONFLICT (evento_id, cliente_id) DO UPDATE SET
                cliente_email = COALESCE(EXCLUDED.cliente_email, comprador_evento.cliente_email),
                evento_nombre = EXCLUDED.evento_nombre,
                evento_fecha = EXCLUDED.evento_fecha,
                evento_ciudad = EXCLUDED.evento_ciudad,
                updated_at = now()
            """, nativeQuery = true)
    int registrar(@Param("id") UUID id,
                  @Param("eventoId") UUID eventoId,
                  @Param("clienteId") UUID clienteId,
                  @Param("clienteEmail") String clienteEmail,
                  @Param("eventoNombre") String eventoNombre,
                  @Param("eventoFecha") Timestamp eventoFecha,
                  @Param("eventoCiudad") String eventoCiudad);
}
