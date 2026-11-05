package ar.edu.unicen.ticketya.inventory.servicio;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class VencimientoScheduler {

    private final VencimientoService vencimientoService;

    public VencimientoScheduler(VencimientoService vencimientoService) {
        this.vencimientoService = vencimientoService;
    }

    @Scheduled(fixedDelay = 30_000)
    public void liberarReservasVencidas() {
        vencimientoService.liberarVencidas();
    }
}
