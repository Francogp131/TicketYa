package ar.edu.unicen.ticketya.catalog.servicio;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventoSchedulerTest {

    @Mock
    private EventService eventService;

    @InjectMocks
    private EventoScheduler eventoScheduler;

    @Test
    void ejecutaFinalizacionYReintentoDeCancelaciones() {
        when(eventService.finalizarEventosVencidos()).thenReturn(1);
        when(eventService.reintentarCancelacionesPendientes()).thenReturn(1);

        eventoScheduler.ejecutar();

        verify(eventService).finalizarEventosVencidos();
        verify(eventService).reintentarCancelacionesPendientes();
    }
}
