package ar.edu.unicen.ticketya.notification.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.unicen.ticketya.notification.dominio.EstadoNotificacion;
import ar.edu.unicen.ticketya.notification.dominio.Notificacion;
import ar.edu.unicen.ticketya.notification.repositorio.NotificacionRepository;
import ar.edu.unicen.ticketya.notification.web.ApiException;
import ar.edu.unicen.ticketya.notification.web.dto.PageResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class ConsultaNotificacionServiceTest {

    private static final UUID CLIENTE = UUID.fromString("33333333-3333-4333-8333-333333333333");

    @Mock
    private NotificacionRepository repository;

    private ConsultaNotificacionService service;

    @BeforeEach
    void setUp() {
        service = new ConsultaNotificacionService(repository);
    }

    @Test
    void listaSoloLasPropiasOrdenadasPorFechaDescendente() {
        Notificacion enviada = notificacion(EstadoNotificacion.ENVIADA, 1);
        Notificacion pendiente = notificacion(EstadoNotificacion.PENDIENTE, 2);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        when(repository.findByDestinatarioId(eq(CLIENTE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(enviada, pendiente)));

        PageResponse<?> pagina = service.listar(CLIENTE, null, 0, 20);

        verify(repository).findByDestinatarioId(eq(CLIENTE), pageable.capture());
        assertThat(pagina.totalElements()).isEqualTo(2);
        assertThat(pageable.getValue().getSort().getOrderFor("createdAt").getDirection())
                .isEqualTo(Sort.Direction.DESC);
        assertThat(pageable.getValue().getSort().getOrderFor("id").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void filtraPorEstado() {
        Notificacion fallida = notificacion(EstadoNotificacion.FALLIDA, 3);
        when(repository.findByDestinatarioIdAndEstado(eq(CLIENTE), eq(EstadoNotificacion.FALLIDA), any()))
                .thenReturn(new PageImpl<>(List.of(fallida)));

        PageResponse<?> pagina = service.listar(CLIENTE, EstadoNotificacion.FALLIDA, 0, 20);

        assertThat(pagina.content()).hasSize(1);
        verify(repository).findByDestinatarioIdAndEstado(eq(CLIENTE), eq(EstadoNotificacion.FALLIDA), any());
    }

    @Test
    void recortaElTamanioMaximoYNormalizaLaPagina() {
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        when(repository.findByDestinatarioId(eq(CLIENTE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listar(CLIENTE, null, -1, 500);

        verify(repository).findByDestinatarioId(eq(CLIENTE), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void notificacionAjenaDevuelve403() {
        UUID notificacionId = UUID.randomUUID();
        Notificacion ajena = notificacion(EstadoNotificacion.ENVIADA, 1);
        ajena.setId(notificacionId);
        ajena.setDestinatarioId(UUID.randomUUID());
        when(repository.findById(notificacionId)).thenReturn(Optional.of(ajena));

        assertThatThrownBy(() -> service.obtener(notificacionId, CLIENTE))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(403);
                    assertThat(ex.getCode()).isEqualTo("NOTIFICATION_FORBIDDEN");
                });
    }

    @Test
    void notificacionInexistenteDevuelve404() {
        UUID notificacionId = UUID.randomUUID();
        when(repository.findById(notificacionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtener(notificacionId, CLIENTE))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(404);
                    assertThat(ex.getCode()).isEqualTo("NOTIFICATION_NOT_FOUND");
                });
    }

    @Test
    void devuelveEstadoEIntentosDeLaNotificacionPropia() {
        UUID notificacionId = UUID.randomUUID();
        Notificacion propia = notificacion(EstadoNotificacion.FALLIDA, 3);
        propia.setId(notificacionId);
        when(repository.findById(notificacionId)).thenReturn(Optional.of(propia));

        var respuesta = service.obtener(notificacionId, CLIENTE);

        assertThat(respuesta.estado()).isEqualTo(EstadoNotificacion.FALLIDA);
        assertThat(respuesta.intentos()).isEqualTo(3);
    }

    private Notificacion notificacion(EstadoNotificacion estado, int intentos) {
        Notificacion notificacion = new Notificacion();
        notificacion.setId(UUID.randomUUID());
        notificacion.setEventId(UUID.randomUUID());
        notificacion.setTipo("orden.pagada");
        notificacion.setDestinatarioId(CLIENTE);
        notificacion.setDestinatarioEmail("cliente@example.com");
        notificacion.setAsunto("Compra confirmada");
        notificacion.setContenido("Detalle");
        notificacion.setEstado(estado);
        notificacion.setIntentos(intentos);
        return notificacion;
    }
}
