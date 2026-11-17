package ar.edu.unicen.ticketya.notification.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.unicen.ticketya.notification.dominio.CompradorEvento;
import ar.edu.unicen.ticketya.notification.evento.OrdenPagadaPayload;
import ar.edu.unicen.ticketya.notification.repositorio.CompradorEventoRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompradorEventoServiceTest {

    @Mock
    private CompradorEventoRepository repository;

    private CompradorEventoService service;

    @BeforeEach
    void setUp() {
        service = new CompradorEventoService(repository);
    }

    @Test
    void registraElCompradorConElSnapshotDelEvento() {
        UUID eventoId = UUID.randomUUID();
        UUID clienteId = UUID.randomUUID();
        Instant fecha = Instant.parse("2027-03-15T00:00:00Z");

        service.registrar(new OrdenPagadaPayload(UUID.randomUUID().toString(), clienteId.toString(),
                eventoId.toString(), "Festival", "FESTIVAL", List.of("Los Piojos"), "Tandil",
                fecha, "PAGADA", " cliente@example.com "));

        verify(repository).registrar(org.mockito.ArgumentMatchers.any(UUID.class), eq(eventoId),
                eq(clienteId), eq("cliente@example.com"), eq("Festival"),
                eq(Timestamp.from(fecha)), eq("Tandil"));
    }

    @Test
    void noGuardaEmailEnBlanco() {
        UUID eventoId = UUID.randomUUID();
        UUID clienteId = UUID.randomUUID();

        service.registrar(new OrdenPagadaPayload(UUID.randomUUID().toString(), clienteId.toString(),
                eventoId.toString(), "Festival", "FESTIVAL", List.of(), "Tandil",
                Instant.parse("2027-03-15T00:00:00Z"), "PAGADA", "   "));

        verify(repository).registrar(org.mockito.ArgumentMatchers.any(UUID.class), eq(eventoId),
                eq(clienteId), isNull(), eq("Festival"), org.mockito.ArgumentMatchers.any(Timestamp.class),
                eq("Tandil"));
    }

    @Test
    void devuelveLosCompradoresDelEvento() {
        UUID eventoId = UUID.randomUUID();
        CompradorEvento comprador = new CompradorEvento();
        comprador.setEventoId(eventoId);
        when(repository.findByEventoIdOrderByClienteIdAsc(eventoId)).thenReturn(List.of(comprador));

        assertThat(service.compradoresDe(eventoId)).containsExactly(comprador);
    }
}
