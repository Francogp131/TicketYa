package ar.edu.unicen.ticketya.orders.servicio;

import ar.edu.unicen.ticketya.orders.dominio.EstadoPago;
import ar.edu.unicen.ticketya.orders.dominio.EstadoReintegro;
import ar.edu.unicen.ticketya.orders.dominio.Pago;
import ar.edu.unicen.ticketya.orders.dominio.Reintegro;
import ar.edu.unicen.ticketya.orders.repositorio.PagoRepository;
import ar.edu.unicen.ticketya.orders.repositorio.ReintegroRepository;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReintegroRetryService {

    private static final Logger log = LoggerFactory.getLogger(ReintegroRetryService.class);

    private final ReintegroRepository reintegroRepository;
    private final PagoRepository pagoRepository;
    private final PaymentGateway paymentGateway;

    public ReintegroRetryService(ReintegroRepository reintegroRepository,
                                 PagoRepository pagoRepository,
                                 PaymentGateway paymentGateway) {
        this.reintegroRepository = reintegroRepository;
        this.pagoRepository = pagoRepository;
        this.paymentGateway = paymentGateway;
    }

    @Scheduled(fixedDelayString = "${orders.reintegros.retry-delay-ms:60000}")
    @Transactional
    public void reintentarPendientes() {
        List<Reintegro> pendientes = reintegroRepository.findByEstado(EstadoReintegro.PENDIENTE);
        for (Reintegro reintegro : pendientes) {
            try {
                paymentGateway.reintegrar(reintegro.getPagoId(), reintegro.getMonto());
                reintegro.setEstado(EstadoReintegro.APROBADO);
                reintegroRepository.save(reintegro);
                actualizarEstadoPago(reintegro.getPagoId());
            } catch (RuntimeException ex) {
                log.warn("No se pudo reintentar reintegro {} para pago {}: {}",
                        reintegro.getId(), reintegro.getPagoId(), ex.getMessage());
            }
        }
    }

    private void actualizarEstadoPago(java.util.UUID pagoId) {
        Pago pago = pagoRepository.findById(pagoId).orElse(null);
        if (pago == null) {
            return;
        }
        List<Reintegro> reintegros = reintegroRepository.findByPagoId(pagoId);
        BigDecimal totalAprobado = reintegros.stream()
                .filter(r -> r.getEstado() == EstadoReintegro.APROBADO)
                .map(Reintegro::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean hayPendientes = reintegros.stream().anyMatch(r -> r.getEstado() == EstadoReintegro.PENDIENTE);
        if (totalAprobado.compareTo(pago.getMonto()) >= 0) {
            pago.setEstado(EstadoPago.REINTEGRADO);
        } else if (hayPendientes) {
            pago.setEstado(EstadoPago.REINTEGRO_PENDIENTE);
        } else if (totalAprobado.compareTo(BigDecimal.ZERO) > 0) {
            pago.setEstado(EstadoPago.PARCIALMENTE_REINTEGRADO);
        }
        pagoRepository.save(pago);
    }
}
