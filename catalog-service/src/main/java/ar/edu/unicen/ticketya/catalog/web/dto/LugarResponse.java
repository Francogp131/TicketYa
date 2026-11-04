package ar.edu.unicen.ticketya.catalog.web.dto;

import ar.edu.unicen.ticketya.catalog.dominio.Lugar;
import java.math.BigDecimal;
import java.util.UUID;

public record LugarResponse(
        UUID id,
        String nombre,
        String direccion,
        String ciudad,
        BigDecimal latitud,
        BigDecimal longitud) {

    public static LugarResponse from(Lugar lugar) {
        return new LugarResponse(
                lugar.getId(),
                lugar.getNombre(),
                lugar.getDireccion(),
                lugar.getCiudad(),
                lugar.getLatitud(),
                lugar.getLongitud());
    }
}
