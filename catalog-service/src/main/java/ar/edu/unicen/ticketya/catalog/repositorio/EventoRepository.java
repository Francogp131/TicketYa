package ar.edu.unicen.ticketya.catalog.repositorio;

import ar.edu.unicen.ticketya.catalog.dominio.EstadoEvento;
import ar.edu.unicen.ticketya.catalog.dominio.Evento;
import ar.edu.unicen.ticketya.catalog.dominio.TipoEvento;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventoRepository extends JpaRepository<Evento, UUID> {

    /**
     * Filtros combinables de la búsqueda pública. La lista se ordena y pagina en el
     * servicio porque el precio mínimo es una agregación por evento.
     */
    @Query("""
            select e from Evento e
            where e.estado in :estados
              and (:tipo is null or e.tipo = :tipo)
              and (:organizadorId is null or e.organizadorId = :organizadorId)
              and e.fecha >= :fechaDesde
              and e.fecha <= :fechaHasta
              and (:ciudad = '' or exists (
                    select 1 from Lugar l
                    where l.id = e.lugarId and lower(l.ciudad) = lower(:ciudad)))
              and (:participante = '' or exists (
                    select 1 from EventoParticipante p
                    where p.eventoId = e.id
                      and lower(p.nombre) like lower(concat('%', :participante, '%'))))
            """)
    List<Evento> buscar(@Param("estados") Collection<EstadoEvento> estados,
                        @Param("tipo") TipoEvento tipo,
                        @Param("organizadorId") UUID organizadorId,
                        @Param("ciudad") String ciudad,
                        @Param("participante") String participante,
                        @Param("fechaDesde") OffsetDateTime fechaDesde,
                        @Param("fechaHasta") OffsetDateTime fechaHasta);

    List<Evento> findByEstadoInAndFechaBefore(Collection<EstadoEvento> estados, OffsetDateTime fecha);

    List<Evento> findByEstadoAndCancelacionPendienteTrue(EstadoEvento estado);
}
