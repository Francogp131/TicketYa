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

    @Query("""
            select e from Evento e, Lugar l
            where e.lugarId = l.id
              and e.estado in :estados
              and e.tipo in :tipos
              and (:filtrarOrganizador = false or e.organizadorId = :organizadorId)
              and (:filtrarParticipante = false or exists (
                    select p.id from EventoParticipante p
                    where p.eventoId = e.id
                      and lower(p.nombre) like :participante escape '\\'))
              and lower(l.ciudad) like :ciudad escape '\\'
              and e.fecha >= :fechaDesde
              and e.fecha <= :fechaHasta
            """)
    List<Evento> buscar(@Param("estados") Collection<EstadoEvento> estados,
                        @Param("tipos") Collection<TipoEvento> tipos,
                        @Param("filtrarOrganizador") boolean filtrarOrganizador,
                        @Param("organizadorId") UUID organizadorId,
                        @Param("filtrarParticipante") boolean filtrarParticipante,
                        @Param("participante") String participante,
                        @Param("ciudad") String ciudad,
                        @Param("fechaDesde") OffsetDateTime fechaDesde,
                        @Param("fechaHasta") OffsetDateTime fechaHasta);

    List<Evento> findByEstadoInAndFechaBefore(Collection<EstadoEvento> estados, OffsetDateTime fecha);

    List<Evento> findByCancelacionPendienteTrue();
}
