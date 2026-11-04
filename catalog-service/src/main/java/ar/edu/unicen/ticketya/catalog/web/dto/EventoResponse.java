package ar.edu.unicen.ticketya.catalog.web.dto;

import ar.edu.unicen.ticketya.catalog.dominio.EstadoEvento;
import ar.edu.unicen.ticketya.catalog.dominio.Evento;
import ar.edu.unicen.ticketya.catalog.dominio.TipoEvento;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record EventoResponse(
        UUID id,
        String nombre,
        String descripcion,
        TipoEvento tipo,
        UUID lugarId,
        String lugarNombre,
        String ciudad,
        UUID organizadorId,
        OffsetDateTime fecha,
        EstadoEvento estado,
        Integer limitePorCliente,
        List<String> participantes,
        List<PrecioResponse> precios) {

    public static EventoResponse from(Evento evento, String lugarNombre, String ciudad,
                                      List<String> participantes, List<PrecioResponse> precios) {
        return new EventoResponse(
                evento.getId(),
                evento.getNombre(),
                evento.getDescripcion(),
                evento.getTipo(),
                evento.getLugarId(),
                lugarNombre,
                ciudad,
                evento.getOrganizadorId(),
                Fechas.argentina(evento.getFecha()),
                evento.getEstado(),
                evento.getLimitePorCliente(),
                participantes,
                precios);
    }
}
