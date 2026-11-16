package ar.edu.unicen.ticketya.notification.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.unicen.ticketya.notification.dominio.EstadoNotificacion;
import ar.edu.unicen.ticketya.notification.dominio.Notificacion;
import ar.edu.unicen.ticketya.notification.repositorio.NotificacionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificacionRepository repository;
    @Mock
    private NotificationSender sender;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-15T12:00:00Z"), ZoneOffset.UTC);
        service = new NotificationService(repository, sender, clock, 3, 30);
    }

    @Test
    void envioExitosoQuedaEnviada() throws Exception {
        Notificacion notificacion = nueva();
        when(repository.findByEventIdAndTipoAndDestinatarioId(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(repository.save(any(Notificacion.class))).thenAnswer(inv -> inv.getArgument(0));

        Notificacion resultado = service.crearYEnviar(notificacion);

        assertThat(resultado.getEstado()).isEqualTo(EstadoNotificacion.ENVIADA);
        assertThat(resultado.getIntentos()).isEqualTo(1);
        assertThat(resultado.getEnviadaAt()).isNotNull();
        assertThat(resultado.getProximoReintento()).isNull();
        assertThat(resultado.getUltimoError()).isNull();
        verify(sender).send(any(Notificacion.class));
    }

    @Test
    void envioFallidoQuedaPendienteConReintento() throws Exception {
        Notificacion notificacion = nueva();
        when(repository.findByEventIdAndTipoAndDestinatarioId(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(repository.save(any(Notificacion.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("smtp caído")).when(sender).send(any(Notificacion.class));

        Notificacion resultado = service.crearYEnviar(notificacion);

        assertThat(resultado.getEstado()).isEqualTo(EstadoNotificacion.PENDIENTE);
        assertThat(resultado.getIntentos()).isEqualTo(1);
        assertThat(resultado.getUltimoError()).contains("smtp");
        assertThat(resultado.getProximoReintento()).isEqualTo("2026-11-15T12:00:30Z");
    }

    @Test
    void alAgotarIntentosQuedaFallida() throws Exception {
        Notificacion existente = nueva();
        existente.setIntentos(2);
        existente.setEstado(EstadoNotificacion.PENDIENTE);
        when(repository.findByEventIdAndTipoAndDestinatarioId(any(), any(), any()))
                .thenReturn(Optional.of(existente));
        when(repository.save(any(Notificacion.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("sin email")).when(sender).send(any(Notificacion.class));

        Notificacion resultado = service.crearYEnviar(existente);

        assertThat(resultado.getEstado()).isEqualTo(EstadoNotificacion.FALLIDA);
        assertThat(resultado.getIntentos()).isEqualTo(3);
        assertThat(resultado.getProximoReintento()).isNull();
    }

    @Test
    void eventoDuplicadoNoReenviaNiDuplica() throws Exception {
        Notificacion existente = nueva();
        existente.setEstado(EstadoNotificacion.ENVIADA);
        existente.setIntentos(1);
        when(repository.findByEventIdAndTipoAndDestinatarioId(
                existente.getEventId(), existente.getTipo(), existente.getDestinatarioId()))
                .thenReturn(Optional.of(existente));

        Notificacion resultado = service.crearYEnviar(existente);

        assertThat(resultado).isSameAs(existente);
        verify(sender, never()).send(any());
        verify(repository, never()).save(any());
    }

    @Test
    void destinatarioSinEmailFallaYMarcaError() throws Exception {
        Notificacion notificacion = nueva();
        notificacion.setDestinatarioEmail(null);
        when(repository.findByEventIdAndTipoAndDestinatarioId(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(repository.save(any(Notificacion.class))).thenAnswer(inv -> inv.getArgument(0));
        // sender real mock lanza por defecto? lo dejamos que el servicio capture excepción del sender mock real
        doThrow(new IllegalArgumentException("El destinatario no tiene email"))
                .when(sender).send(any(Notificacion.class));

        Notificacion resultado = service.crearYEnviar(notificacion);

        assertThat(resultado.getEstado()).isEqualTo(EstadoNotificacion.PENDIENTE);
        assertThat(resultado.getUltimoError()).contains("no tiene email");
    }

    private Notificacion nueva() {
        Notificacion n = new Notificacion();
        n.setEventId(UUID.randomUUID());
        n.setTipo("orden.pagada");
        n.setDestinatarioId(UUID.randomUUID());
        n.setDestinatarioEmail("cliente@example.com");
        n.setAsunto("Compra confirmada");
        n.setContenido("Detalle");
        n.setCorrelationId("corr-1");
        return n;
    }
}
