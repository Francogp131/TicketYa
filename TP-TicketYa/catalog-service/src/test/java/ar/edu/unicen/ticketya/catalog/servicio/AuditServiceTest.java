package ar.edu.unicen.ticketya.catalog.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import ar.edu.unicen.ticketya.catalog.dominio.AuditLog;
import ar.edu.unicen.ticketya.catalog.repositorio.AuditLogRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    private static final UUID EVENTO = UUID.randomUUID();
    private static final UUID USUARIO = UUID.randomUUID();

    @Mock
    private AuditLogRepository repository;

    private AuditService service;

    @BeforeEach
    void setUp() {
        service = new AuditService(repository);
    }

    @Test
    void registraUsuarioResultadoDetalleYCorrelacion() {
        service.registrar(EVENTO, "PUBLICAR_EVENTO", USUARIO, "ORGANIZER", "EXITOSO",
                "estado=PUBLICADO", "corr-1");

        ArgumentCaptor<AuditLog> audit = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(audit.capture());
        assertThat(audit.getValue().getEventoId()).isEqualTo(EVENTO);
        assertThat(audit.getValue().getAccion()).isEqualTo("PUBLICAR_EVENTO");
        assertThat(audit.getValue().getUsuarioId()).isEqualTo(USUARIO);
        assertThat(audit.getValue().getUsuarioRol()).isEqualTo("ORGANIZER");
        assertThat(audit.getValue().getResultado()).isEqualTo("EXITOSO");
        assertThat(audit.getValue().getDetalle()).isEqualTo("estado=PUBLICADO");
        assertThat(audit.getValue().getCorrelationId()).isEqualTo("corr-1");
    }

    @Test
    void recortaCorrelacionesLargas() {
        service.registrar(EVENTO, "CANCELAR_EVENTO", USUARIO, "ADMIN", "EXITOSO",
                "estado=CANCELADO", "x".repeat(200));

        ArgumentCaptor<AuditLog> audit = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(audit.capture());
        assertThat(audit.getValue().getCorrelationId()).hasSize(120);
    }

    @Test
    void unFalloDeLaBitacoraNoPropagaLaExcepcion() {
        doThrow(new RuntimeException("base caída")).when(repository).save(any(AuditLog.class));

        assertThatCode(() -> service.registrar(EVENTO, "EDITAR_EVENTO", USUARIO, "ORGANIZER",
                "RECHAZADO", "EVENT_NOT_EDITABLE", "corr-2"))
                .doesNotThrowAnyException();
    }
}
