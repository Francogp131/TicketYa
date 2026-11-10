package ar.edu.unicen.ticketya.orders.servicio;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.unicen.ticketya.orders.dominio.Devolucion;
import ar.edu.unicen.ticketya.orders.dominio.EstadoDevolucion;
import ar.edu.unicen.ticketya.orders.repositorio.DevolucionRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DevolucionRetryServiceTest {

    @Mock
    private DevolucionRepository devolucionRepository;
    @Mock
    private DevolucionService devolucionService;

    private DevolucionRetryService retryService;

    @BeforeEach
    void setUp() {
        retryService = new DevolucionRetryService(devolucionRepository, devolucionService);
    }

    @Test
    void reintentaTodasLasDevolucionesProcesando() {
        Devolucion primera = devolucion();
        Devolucion segunda = devolucion();
        when(devolucionRepository.findByEstado(EstadoDevolucion.PROCESANDO))
                .thenReturn(List.of(primera, segunda));

        retryService.reintentarProcesando();

        verify(devolucionService).aprobar(primera.getId());
        verify(devolucionService).aprobar(segunda.getId());
    }

    @Test
    void siUnaFallaSigueConLaSiguiente() {
        Devolucion primera = devolucion();
        Devolucion segunda = devolucion();
        when(devolucionRepository.findByEstado(EstadoDevolucion.PROCESANDO))
                .thenReturn(List.of(primera, segunda));
        doThrow(new RuntimeException("pasarela caída"))
                .when(devolucionService).aprobar(primera.getId());

        retryService.reintentarProcesando();

        verify(devolucionService).aprobar(primera.getId());
        verify(devolucionService).aprobar(segunda.getId());
    }

    private Devolucion devolucion() {
        Devolucion devolucion = new Devolucion();
        devolucion.setId(UUID.randomUUID());
        devolucion.setEstado(EstadoDevolucion.PROCESANDO);
        return devolucion;
    }
}
