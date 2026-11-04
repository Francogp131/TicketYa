package ar.edu.unicen.ticketya.catalog.web.dto;

import java.time.OffsetDateTime;
import java.time.ZoneId;

public final class Fechas {

    public static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");

    private Fechas() {
    }

    public static OffsetDateTime argentina(OffsetDateTime fecha) {
        return fecha == null ? null : fecha.atZoneSameInstant(ZONA).toOffsetDateTime();
    }
}
