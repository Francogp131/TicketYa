package ar.edu.unicen.ticketya.notification.evento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
class OrdenPagadaListenerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private CompradorEventoService compradorEventoService;

    private OrdenPagadaListener listener;

    @BeforeEach
    void setUp() {
        listener = new OrdenPagadaListener(notificationService, compradorEventoService,
                new DestinatarioEmail("ticketya.local"));
    }

    @Test
    void generaLaNotificacionDeCompraYUsaEmailDeterministicoSiFalta() {
        UUID eventId = UUID.randomUUID();
        UUID clienteId = UUID.randomUUID();
        UUID eventoId = UUID.randomUUID();
        when(notificationService.crearYEnviar(any(Notificacion.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        listener.escuchar(evento(eventId, clienteId, eventoId, OrdenPagadaEvent.TIPO,
                OrdenPagadaPayload.ESTADO_PAGADA, null));

        ArgumentCaptor<Notificacion> captor = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificationService).crearYEnviar(captor.capture());
        verify(compradorEventoService).registrar(any(OrdenPagadaPayload.class));
        Notificacion notificacion = captor.getValue();
        assertThat(notificacion.getEventId()).isEqualTo(eventId);
        assertThat(notificacion.getTipo()).isEqualTo(OrdenPagadaEvent.TIPO);
        assertThat(notificacion.getDestinatarioId()).isEqualTo(clienteId);
        assertThat(notificacion.getDestinatarioEmail())
                .isEqualTo("cliente-" + clienteId + "@ticketya.local");
        assertThat(notificacion.getAsunto()).contains("Festival de Rock");
        assertThat(notificacion.getContenido()).contains("fue confirmada");
        assertThat(notificacion.getCorrelationId()).isEqualTo("corr-1");
    }

    @Test
    void respetaElEmailDelPayloadCuandoViene() {
        when(notificationService.crearYEnviar(any(Notificacion.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        listener.escuchar(evento(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                OrdenPagadaEvent.TIPO, OrdenPagadaPayload.ESTADO_PAGADA, "cliente@example.com"));

        ArgumentCaptor<Notificacion> captor = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificationService).crearYEnviar(captor.capture());
        assertThat(captor.getValue().getDestinatarioEmail()).isEqualTo("cliente@example.com");
    }

    @Test
    void unEstadoDistintoDePagadaVaALaDlqSinNotificar() {
        OrdenPagadaEvent evento = evento(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                OrdenPagadaEvent.TIPO, "PENDIENTE", null);

        assertThatThrownBy(() -> listener.escuchar(evento))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(notificationService);
    }

    @Test
    void unTipoInesperadoVaALaDlqSinNotificar() {
        OrdenPagadaEvent evento = evento(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "orden.reintegrada", OrdenPagadaPayload.ESTADO_PAGADA, null);

        assertThatThrownBy(() -> listener.escuchar(evento))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(notificationService);
    }

    @Test
    void unPayloadSinClienteVaALaDlqSinNotificar() {
        OrdenPagadaEvent evento = new OrdenPagadaEvent(UUID.randomUUID().toString(),
                OrdenPagadaEvent.TIPO, Instant.parse("2026-11-15T12:00:00Z"), "corr-1", 1,
                new OrdenPagadaPayload(UUID.randomUUID().toString(), null, UUID.randomUUID().toString(),
                        "Festival", "FESTIVAL", List.of(), "Tandil",
                        Instant.parse("2027-03-15T00:00:00Z"), OrdenPagadaPayload.ESTADO_PAGADA, null));

        assertThatThrownBy(() -> listener.escuchar(evento))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(notificationService);
    }

    @Test
    void unEventIdQueNoEsUuidVaALaDlqSinNotificar() {
        OrdenPagadaEvent evento = new OrdenPagadaEvent("no-es-uuid", OrdenPagadaEvent.TIPO,
                Instant.parse("2026-11-15T12:00:00Z"), "corr-1", 1,
                new OrdenPagadaPayload(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                        UUID.randomUUID().toString(), "Festival", "FESTIVAL", List.of(), "Tandil",
                        Instant.parse("2027-03-15T00:00:00Z"), OrdenPagadaPayload.ESTADO_PAGADA, null));

        assertThatThrownBy(() -> listener.escuchar(evento))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(notificationService);
    }

    private OrdenPagadaEvent evento(UUID eventId, UUID clienteId, UUID eventoId, String tipo,
                                    String estado, String clienteEmail) {
        OrdenPagadaPayload payload = new OrdenPagadaPayload(UUID.randomUUID().toString(),
                clienteId.toString(), eventoId.toString(), "Festival de Rock Tandil 2027", "FESTIVAL",
                List.of("Los Piojos"), "Tandil", Instant.parse("2027-03-15T00:00:00Z"), estado,
                clienteEmail);
        return new OrdenPagadaEvent(eventId.toString(), tipo, Instant.parse("2026-11-15T12:00:00Z"),
                "corr-1", 1, payload);
    }
}
