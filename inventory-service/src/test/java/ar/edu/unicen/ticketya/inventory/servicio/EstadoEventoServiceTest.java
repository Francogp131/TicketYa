package ar.edu.unicen.ticketya.inventory.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.unicen.ticketya.inventory.dominio.EstadoEventoInventario;
import ar.edu.unicen.ticketya.inventory.dominio.EventoInventario;
import ar.edu.unicen.ticketya.inventory.repositorio.EventoInventarioRepository;
import ar.edu.unicen.ticketya.inventory.web.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EstadoEventoServiceTest {

    @Mock
    private EventoInventarioRepository eventoRepository;

    private EstadoEventoService service;
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-09T15:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        service = new EstadoEventoService(eventoRepository, clock);
    }

    @Test
    void cambiaElEstadoYLaFechaDeActualizacion() {
        EventoInventario evento = publicado();
        when(eventoRepository.findById(evento.getEventoId())).thenReturn(Optional.of(evento));

        service.actualizar(evento.getEventoId(), "CANCELADO");

        assertThat(evento.getEstado()).isEqualTo(EstadoEventoInventario.CANCELADO);
        assertThat(evento.getUpdatedAt()).isEqualTo(OffsetDateTime.now(clock));
        verify(eventoRepository).save(evento);
    }

    @Test
    void repetirElMismoEstadoNoEscribe() {
        EventoInventario evento = publicado();
        evento.setEstado(EstadoEventoInventario.FINALIZADO);
        when(eventoRepository.findById(evento.getEventoId())).thenReturn(Optional.of(evento));

        service.actualizar(evento.getEventoId(), "FINALIZADO");

        verify(eventoRepository, never()).save(evento);
    }

    @Test
    void unEventoInexistenteResponde404() {
        UUID eventoId = UUID.randomUUID();
        when(eventoRepository.findById(eventoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.actualizar(eventoId, "CANCELADO"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(404);
                    assertThat(ex.getCode()).isEqualTo("EVENT_NOT_FOUND");
                });
    }

    @Test
    void unEstadoInvalidoResponde400() {
        assertThatThrownBy(() -> service.actualizar(UUID.randomUUID(), "PUBLICADO"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(400);
                    assertThat(ex.getCode()).isEqualTo("VALIDATION_ERROR");
                });
        verify(eventoRepository, never()).findById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void noSePuedeSalirDeUnEstadoTerminal() {
        EventoInventario evento = publicado();
        evento.setEstado(EstadoEventoInventario.CANCELADO);
        when(eventoRepository.findById(evento.getEventoId())).thenReturn(Optional.of(evento));

        assertThatThrownBy(() -> service.actualizar(evento.getEventoId(), "FINALIZADO"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(409);
                    assertThat(ex.getCode()).isEqualTo("EVENT_NOT_EDITABLE");
                });
        verify(eventoRepository, never()).save(evento);
    }

    private EventoInventario publicado() {
        EventoInventario evento = new EventoInventario();
        evento.setEventoId(UUID.randomUUID());
        evento.setEstado(EstadoEventoInventario.PUBLICADO);
        evento.setUpdatedAt(OffsetDateTime.parse("2026-11-01T00:00:00Z"));
        return evento;
    }
}
