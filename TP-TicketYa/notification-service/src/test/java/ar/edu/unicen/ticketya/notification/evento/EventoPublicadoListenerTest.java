package ar.edu.unicen.ticketya.notification.evento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.unicen.ticketya.notification.dominio.Notificacion;
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
class EventoPublicadoListenerTest {

    @Mock
    private PreferenceClient preferenceClient;

    @Mock
    private NotificationService notificationService;

    private EventoPublicadoListener listener;

    @BeforeEach
    void setUp() {
        listener = new EventoPublicadoListener(preferenceClient, notificationService,
                new DestinatarioEmail("ticketya.local"));
    }

    @Test
    void notificaUnaSolaVezPorClienteAunqueCoincidanVariosArtistas() {
        UUID cliente = UUID.randomUUID();
        UUID otro = UUID.randomUUID();
        when(preferenceClient.buscarPorArtistaYCiudad("Los Piojos", "Tandil"))
                .thenReturn(List.of(new PreferenciaCliente(cliente.toString(), null)));
        when(preferenceClient.buscarPorArtistaYCiudad("Callejeros", "Tandil"))
                .thenReturn(List.of(new PreferenciaCliente(cliente.toString(), "cliente@example.com"),
                        new PreferenciaCliente(otro.toString(), null)));

        listener.escuchar(evento(List.of("Los Piojos", "Callejeros"), "Tandil"));

        ArgumentCaptor<Notificacion> captor = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificationService, org.mockito.Mockito.times(2)).crearYEnviar(captor.capture());
        assertThat(captor.getAllValues()).extracting(Notificacion::getDestinatarioId)
                .containsExactly(cliente, otro);
        assertThat(captor.getAllValues()).allSatisfy(notificacion -> {
            assertThat(notificacion.getTipo()).isEqualTo(EventoEvent.PUBLICADO);
            assertThat(notificacion.getAsunto()).contains("Nuevo evento");
            assertThat(notificacion.getContenido()).contains("Tandil", "Los Piojos");
        });
        assertThat(captor.getAllValues().get(1).getDestinatarioEmail())
                .isEqualTo("cliente-" + otro + "@ticketya.local");
    }

    @Test
    void sinCoincidenciasNoGeneraAvisos() {
        when(preferenceClient.buscarPorArtistaYCiudad("Los Piojos", "Tandil")).thenReturn(List.of());

        listener.escuchar(evento(List.of("Los Piojos"), "Tandil"));

        verifyNoInteractions(notificationService);
    }

    @Test
    void unEventoSinCiudadNoConsultaPreferencias() {
        listener.escuchar(evento(List.of("Los Piojos"), null));

        verifyNoInteractions(preferenceClient, notificationService);
    }

    @Test
    void unTipoInesperadoVaALaDlq() {
        EventoEvent evento = new EventoEvent(UUID.randomUUID().toString(), EventoEvent.CANCELADO,
                Instant.parse("2026-11-15T12:00:00Z"), "corr-1", 1,
                new EventoPayload(UUID.randomUUID().toString(), "Festival", "FESTIVAL",
                        List.of("Los Piojos"), "Tandil", Instant.parse("2027-03-15T00:00:00Z"),
                        null, "CANCELADO"));

        assertThatThrownBy(() -> listener.escuchar(evento))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(notificationService);
    }

    private EventoEvent evento(List<String> artistas, String ciudad) {
        EventoPayload payload = new EventoPayload(UUID.randomUUID().toString(), "Festival de Rock",
                "FESTIVAL", artistas, ciudad, Instant.parse("2027-03-15T00:00:00Z"), null, "PUBLICADO");
        return new EventoEvent(UUID.randomUUID().toString(), EventoEvent.PUBLICADO,
                Instant.parse("2026-11-15T12:00:00Z"), "corr-1", 1, payload);
    }
}
