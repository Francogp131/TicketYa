package ar.edu.unicen.ticketya.orders.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.unicen.ticketya.orders.dominio.EstadoPago;
import ar.edu.unicen.ticketya.orders.dominio.EstadoReintegro;
import ar.edu.unicen.ticketya.orders.dominio.Pago;
import ar.edu.unicen.ticketya.orders.dominio.Reintegro;
import ar.edu.unicen.ticketya.orders.repositorio.PagoRepository;
import ar.edu.unicen.ticketya.orders.repositorio.ReintegroRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReintegroRetryServiceTest {

    @Mock
    private ReintegroRepository reintegroRepository;
    @Mock
    private PagoRepository pagoRepository;
    @Mock
    private PaymentGateway paymentGateway;

    private ReintegroRetryService retryService;

    @BeforeEach
    void setUp() {
        retryService = new ReintegroRetryService(reintegroRepository, pagoRepository, paymentGateway);
    }

    @Test
    void apruebaElPendienteYActualizaElPago() {
        UUID pagoId = UUID.randomUUID();
        Pago pago = pago(pagoId, new BigDecimal("15000.00"));
        Reintegro reintegro = reintegro(pagoId, new BigDecimal("15000.00"));
        when(reintegroRepository.findByEstado(EstadoReintegro.PENDIENTE)).thenReturn(List.of(reintegro));
        when(reintegroRepository.findByPagoId(pagoId)).thenReturn(List.of(reintegro));
        when(pagoRepository.findById(pagoId)).thenReturn(Optional.of(pago));

        retryService.reintentarPendientes();

        verify(paymentGateway).reintegrar(pagoId, new BigDecimal("15000.00"));
        assertThat(reintegro.getEstado()).isEqualTo(EstadoReintegro.APROBADO);
        verify(reintegroRepository).save(reintegro);
        verify(pagoRepository).save(pago);
        assertThat(pago.getEstado()).isEqualTo(EstadoPago.REINTEGRADO);
    }

    @Test
    void siLaPasarelaFallaElReintegroSiguePendiente() {
        UUID pagoId = UUID.randomUUID();
        Reintegro reintegro = reintegro(pagoId, new BigDecimal("15000.00"));
        when(reintegroRepository.findByEstado(EstadoReintegro.PENDIENTE)).thenReturn(List.of(reintegro));
        doThrow(new RuntimeException("pasarela caída"))
                .when(paymentGateway).reintegrar(pagoId, new BigDecimal("15000.00"));

        retryService.reintentarPendientes();

        assertThat(reintegro.getEstado()).isEqualTo(EstadoReintegro.PENDIENTE);
        verify(reintegroRepository, never()).save(any());
        verify(pagoRepository, never()).save(any());
    }

    private Reintegro reintegro(UUID pagoId, BigDecimal monto) {
        Reintegro reintegro = new Reintegro(pagoId, UUID.randomUUID(), monto, EstadoReintegro.PENDIENTE);
        reintegro.setId(UUID.randomUUID());
        return reintegro;
    }

    private Pago pago(UUID pagoId, BigDecimal monto) {
        Pago pago = new Pago();
        pago.setId(pagoId);
        pago.setMonto(monto);
        pago.setEstado(EstadoPago.APROBADO);
        return pago;
    }
}
