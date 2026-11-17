package ar.edu.unicen.ticketya.notification.evento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.unicen.ticketya.notification.dominio.CompradorEvento;
import ar.edu.unicen.ticketya.notification.dominio.Notificacion;
import ar.edu.unicen.ticketya.notification.servicio.CompradorEventoService;
import ar.edu.unicen.ticketya.notification.servicio.DestinatarioEmail;
import ar.edu.unicen.ticketya.notification.servicio.NotificationService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

@ExtendWith(MockitoExtension.class)
class EventoNotificacionListenerTest {

    @Mock
    private CompradorEventoService compradorEventoService;

    @Mock
    private NotificationService notificationService;

    private EventoNotificacionListener listener;

    @BeforeEach
    void setUp() {
        listener = new EventoNotificacionListener(compradorEventoService, notificationService,
                new DestinatarioEmail("ticketya.local"));
    }

    @Test
    void laCancelacionNotificaATodosLosCompradores() {
        UUID eventoId = UUID.randomUUID();
        CompradorEvento uno = comprador(eventoId, null);
        CompradorEvento dos = comprador(eventoId, "dos@example.com");
        when(compradorEventoService.compradoresDe(eventoId)).thenReturn(List.of(uno, dos));

        listener.escuchar(evento(EventoEvent.CANCELADO, eventoId));

        ArgumentCaptor<Notificacion> captor = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificationService, times(2)).crearYEnviar(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(notificacion -> {
            assertThat(notificacion.getTipo()).isEqualTo(EventoEvent.CANCELADO);
            assertThat(notificacion.getAsunto()).contains("Evento cancelado");
            assertThat(notificacion.getContenido()).contains("serán reintegradas");
        });
        assertThat(captor.getAllValues()).extracting(Notificacion::getDestinatarioId)
                .containsExactly(uno.getClienteId(), dos.getClienteId());
        assertThat(captor.getAllValues().get(0).getDestinatarioEmail())
                .isEqualTo("cliente-" + uno.getClienteId() + "@ticketya.local");
        assertThat(captor.getAllValues().get(1).getDestinatarioEmail()).isEqualTo("dos@example.com");
    }

    @Test
    void laActualizacionIncluyeElDatoAnteriorYElNuevo() {
        UUID eventoId = UUID.randomUUID();
        CompradorEvento comprador = comprador(eventoId, null);
        when(compradorEventoService.compradoresDe(eventoId)).thenReturn(List.of(comprador));

        listener.escuchar(evento(EventoEvent.ACTUALIZADO, eventoId));

        ArgumentCaptor<Notificacion> captor = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificationService).crearYEnviar(captor.capture());
        assertThat(captor.getValue().getAsunto()).contains("Evento actualizado");
        assertThat(captor.getValue().getContenido())
                .contains("Antes", "2027-03-15T00:00:00Z", "Tandil")
                .contains("Ahora", "2027-04-20T00:00:00Z", "Mar del Plata");
    }

    @Test
    void unEventoSinCompradoresNoGeneraAvisos() {
        UUID eventoId = UUID.randomUUID();
        when(compradorEventoService.compradoresDe(eventoId)).thenReturn(List.of());

        listener.escuchar(evento(EventoEvent.CANCELADO, eventoId));

        verifyNoInteractions(notificationService);
    }

    @Test
    void unTipoInesperadoVaALaDlq() {
        assertThatThrownBy(() -> listener.escuchar(evento(EventoEvent.PUBLICADO, UUID.randomUUID())))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(notificationService);
    }

    @Test
    void unEventoIdInvalidoVaALaDlq() {
        EventoEvent evento = new EventoEvent("no-es-uuid", EventoEvent.CANCELADO,
                Instant.parse("2026-11-15T12:00:00Z"), "corr-1", 1,
                new EventoPayload(UUID.randomUUID().toString(), "Festival", "FESTIVAL", List.of(),
                        "Tandil", Instant.parse("2027-03-15T00:00:00Z"), null, "CANCELADO"));

        assertThatThrownBy(() -> listener.escuchar(evento))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(notificationService);
    }

    private EventoEvent evento(String tipo, UUID eventoId) {
        EventoPayload payload = new EventoPayload(eventoId.toString(), "Festival de Rock",
                "FESTIVAL", List.of("Los Piojos"), "Mar del Plata",
                Instant.parse("2027-04-20T00:00:00Z"), null,
                EventoEvent.CANCELADO.equals(tipo) ? "CANCELADO" : "PUBLICADO");
        return new EventoEvent(UUID.randomUUID().toString(), tipo,
                Instant.parse("2026-11-15T12:00:00Z"), "corr-1", 1, payload);
    }

    private CompradorEvento comprador(UUID eventoId, String email) {
        CompradorEvento comprador = new CompradorEvento();
        comprador.setEventoId(eventoId);
        comprador.setClienteId(UUID.randomUUID());
        comprador.setClienteEmail(email);
        comprador.setEventoNombre("Festival de Rock");
        comprador.setEventoFecha(Instant.parse("2027-03-15T00:00:00Z"));
        comprador.setEventoCiudad("Tandil");
        return comprador;
    }
}
