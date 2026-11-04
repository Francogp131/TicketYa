package ar.edu.unicen.ticketya.inventory.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.unicen.ticketya.inventory.dominio.EventoInventario;
import ar.edu.unicen.ticketya.inventory.dominio.SectorInventario;
import ar.edu.unicen.ticketya.inventory.repositorio.EventoInventarioRepository;
import ar.edu.unicen.ticketya.inventory.repositorio.SectorInventarioRepository;
import ar.edu.unicen.ticketya.inventory.web.ApiException;
import ar.edu.unicen.ticketya.inventory.web.dto.InventoryUpsertRequest;
import ar.edu.unicen.ticketya.inventory.web.dto.InventoryUpsertResponse;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class InventarioServiceTest {

    @Mock
    private EventoInventarioRepository eventoRepository;
    @Mock
    private SectorInventarioRepository sectorRepository;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private InventarioService inventarioService;

    @BeforeEach
    void setUp() {
        inventarioService = new InventarioService(eventoRepository, sectorRepository, jdbcTemplate);
    }

    @Test
    void laMismaVersionEsIdempotenteYNoDuplicaFilas() {
        UUID eventoId = UUID.randomUUID();
        EventoInventario existente = new EventoInventario();
        existente.setEventoId(eventoId);
        existente.setVersion(2L);
        when(eventoRepository.findById(eventoId)).thenReturn(Optional.of(existente));

        InventoryUpsertResponse response = inventarioService.upsert(eventoId, request(eventoId, 2L));

        assertThat(response.idempotente()).isTrue();
        verify(eventoRepository, never()).saveAndFlush(any());
        verify(jdbcTemplate, never()).batchUpdate(any(String.class), anyList());
    }

    @Test
    void unaVersionNuevaInsertaSectorYAsientos() {
        UUID eventoId = UUID.randomUUID();
        when(eventoRepository.findById(eventoId)).thenReturn(Optional.empty());
        when(eventoRepository.saveAndFlush(any(EventoInventario.class))).thenAnswer(inv -> inv.getArgument(0));
        when(sectorRepository.findByEventoIdAndSectorId(any(), any())).thenReturn(Optional.empty());
        when(sectorRepository.save(any(SectorInventario.class))).thenAnswer(inv -> {
            SectorInventario sector = inv.getArgument(0);
            sector.setId(UUID.randomUUID());
            return sector;
        });

        InventoryUpsertResponse response = inventarioService.upsert(eventoId, request(eventoId, 1L));

        assertThat(response.idempotente()).isFalse();
        assertThat(response.sectores()).isEqualTo(1);
        assertThat(response.asientos()).isEqualTo(2);
        verify(jdbcTemplate).batchUpdate(any(String.class), anyList());
    }

    @Test
    void elEventoDeLaRutaDebeCoincidirConElCuerpo() {
        UUID eventoId = UUID.randomUUID();
        InventoryUpsertRequest request = request(UUID.randomUUID(), 1L);

        assertThatThrownBy(() -> inventarioService.upsert(eventoId, request))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(400);
                    assertThat(ex.getCode()).isEqualTo("VALIDATION_ERROR");
                });
    }

    private InventoryUpsertRequest request(UUID eventoId, long version) {
        InventoryUpsertRequest.AsientoRequest a1 = new InventoryUpsertRequest.AsientoRequest(UUID.randomUUID(), 1, 1);
        InventoryUpsertRequest.AsientoRequest a2 = new InventoryUpsertRequest.AsientoRequest(UUID.randomUUID(), 1, 2);
        InventoryUpsertRequest.SectorRequest sector = new InventoryUpsertRequest.SectorRequest(
                UUID.randomUUID(), "Platea", "NUMERADO", new BigDecimal("15000"), 2, 1, List.of(a1, a2));
        return new InventoryUpsertRequest(eventoId, "Recital",
                OffsetDateTime.parse("2027-01-15T21:00:00-03:00"), 4, version, List.of(sector));
    }
}
