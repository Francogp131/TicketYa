package ar.edu.unicen.ticketya.catalog.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.unicen.ticketya.catalog.dominio.Asiento;
import ar.edu.unicen.ticketya.catalog.dominio.Lugar;
import ar.edu.unicen.ticketya.catalog.dominio.Sector;
import ar.edu.unicen.ticketya.catalog.dominio.TipoSector;
import ar.edu.unicen.ticketya.catalog.repositorio.AsientoRepository;
import ar.edu.unicen.ticketya.catalog.repositorio.LugarRepository;
import ar.edu.unicen.ticketya.catalog.repositorio.SectorRepository;
import ar.edu.unicen.ticketya.catalog.web.ApiException;
import ar.edu.unicen.ticketya.catalog.web.dto.CrearSectorRequest;
import ar.edu.unicen.ticketya.catalog.web.dto.SectorResponse;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VenueServiceTest {

    @Mock
    private LugarRepository lugarRepository;

    @Mock
    private SectorRepository sectorRepository;

    @Mock
    private AsientoRepository asientoRepository;

    @InjectMocks
    private VenueService venueService;

    @Test
    void unSectorNumeradoGeneraTodosLosAsientos() {
        UUID lugarId = UUID.randomUUID();
        Lugar lugar = new Lugar();
        lugar.setId(lugarId);
        when(lugarRepository.findById(lugarId)).thenReturn(Optional.of(lugar));
        when(sectorRepository.save(any(Sector.class))).thenAnswer(invocation -> {
            Sector sector = invocation.getArgument(0);
            sector.setId(UUID.randomUUID());
            return sector;
        });

        SectorResponse response = venueService.crearSector(lugarId,
                new CrearSectorRequest("Platea", TipoSector.NUMERADO, null, 2, 3));

        assertThat(response.capacidad()).isEqualTo(6);
        assertThat(response.asientosGenerados()).isEqualTo(6);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.List<Asiento>> captor = ArgumentCaptor.forClass(java.util.List.class);
        verify(asientoRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(6);
        assertThat(captor.getValue()).extracting(Asiento::getFila).containsOnly(1, 2);
        assertThat(captor.getValue()).extracting(Asiento::getNumero).containsOnly(1, 2, 3);
    }

    @Test
    void unSectorNoNumeradoSinCapacidadEsInvalido() {
        UUID lugarId = UUID.randomUUID();
        Lugar lugar = new Lugar();
        lugar.setId(lugarId);
        when(lugarRepository.findById(lugarId)).thenReturn(Optional.of(lugar));

        assertThatThrownBy(() -> venueService.crearSector(lugarId,
                new CrearSectorRequest("Campo", TipoSector.NO_NUMERADO, null, null, null)))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(400);
                    assertThat(ex.getCode()).isEqualTo("VALIDATION_ERROR");
                });
    }

    @Test
    void unSectorDuplicadoDevuelve409YNoSeGuarda() {
        UUID lugarId = UUID.randomUUID();
        Lugar lugar = new Lugar();
        lugar.setId(lugarId);
        when(lugarRepository.findById(lugarId)).thenReturn(Optional.of(lugar));
        when(sectorRepository.existsByLugarIdAndNombre(lugarId, "Platea")).thenReturn(true);

        assertThatThrownBy(() -> venueService.crearSector(lugarId,
                new CrearSectorRequest(" Platea ", TipoSector.NO_NUMERADO, 100, null, null)))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(409);
                    assertThat(ex.getCode()).isEqualTo("SECTOR_ALREADY_EXISTS");
                });

        verify(sectorRepository, never()).save(any());
    }

    @Test
    void unSectorNumeradoQueSuperaLaCapacidadMaximaEsInvalido() {
        UUID lugarId = UUID.randomUUID();
        Lugar lugar = new Lugar();
        lugar.setId(lugarId);
        when(lugarRepository.findById(lugarId)).thenReturn(Optional.of(lugar));

        assertThatThrownBy(() -> venueService.crearSector(lugarId,
                new CrearSectorRequest("Platea", TipoSector.NUMERADO, null, 100, 201)))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(400);
                    assertThat(ex.getCode()).isEqualTo("VALIDATION_ERROR");
                });

        verify(sectorRepository, never()).save(any());
    }

    @Test
    void unSectorNoNumeradoQueSuperaLaCapacidadMaximaEsInvalido() {
        UUID lugarId = UUID.randomUUID();
        Lugar lugar = new Lugar();
        lugar.setId(lugarId);
        when(lugarRepository.findById(lugarId)).thenReturn(Optional.of(lugar));

        assertThatThrownBy(() -> venueService.crearSector(lugarId,
                new CrearSectorRequest("Campo", TipoSector.NO_NUMERADO, 20001, null, null)))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(400);
                    assertThat(ex.getCode()).isEqualTo("VALIDATION_ERROR");
                });

        verify(sectorRepository, never()).save(any());
    }

    @Test
    void unLugarInexistenteDevuelve404() {
        UUID lugarId = UUID.randomUUID();
        when(lugarRepository.findById(lugarId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> venueService.obtenerLugar(lugarId))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(404);
                    assertThat(ex.getCode()).isEqualTo("VENUE_NOT_FOUND");
                });
    }
}
