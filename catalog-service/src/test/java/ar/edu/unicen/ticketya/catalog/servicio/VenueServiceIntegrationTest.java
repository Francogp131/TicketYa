package ar.edu.unicen.ticketya.catalog.servicio;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.unicen.ticketya.catalog.dominio.Asiento;
import ar.edu.unicen.ticketya.catalog.dominio.TipoSector;
import ar.edu.unicen.ticketya.catalog.repositorio.AsientoRepository;
import ar.edu.unicen.ticketya.catalog.web.dto.CrearLugarRequest;
import ar.edu.unicen.ticketya.catalog.web.dto.CrearSectorRequest;
import ar.edu.unicen.ticketya.catalog.web.dto.LugarResponse;
import ar.edu.unicen.ticketya.catalog.web.dto.SectorResponse;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class VenueServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private VenueService venueService;

    @Autowired
    private AsientoRepository asientoRepository;

    @Test
    void unSectorNumeradoGeneraExactamenteFilasPorAsientosEnPostgres() {
        LugarResponse lugar = venueService.crearLugar(lugarRequest());

        SectorResponse sector = venueService.crearSector(lugar.id(),
                new CrearSectorRequest("Platea", TipoSector.NUMERADO, null, 2, 3));

        List<Asiento> asientos = asientoRepository.findBySectorIdOrderByFilaAscNumeroAsc(sector.id());
        assertThat(sector.capacidad()).isEqualTo(6);
        assertThat(sector.asientosGenerados()).isEqualTo(6);
        assertThat(asientos).hasSize(6);
        assertThat(asientos)
                .extracting(asiento -> asiento.getFila() + ":" + asiento.getNumero())
                .containsExactly("1:1", "1:2", "1:3", "2:1", "2:2", "2:3");
    }

    @Test
    void unSectorNoNumeradoNoGeneraAsientosEnPostgres() {
        LugarResponse lugar = venueService.crearLugar(lugarRequest());

        SectorResponse sector = venueService.crearSector(lugar.id(),
                new CrearSectorRequest("Campo", TipoSector.NO_NUMERADO, 5000, null, null));

        assertThat(sector.capacidad()).isEqualTo(5000);
        assertThat(sector.asientosGenerados()).isZero();
        assertThat(asientoRepository.findBySectorIdOrderByFilaAscNumeroAsc(sector.id())).isEmpty();
    }

    private CrearLugarRequest lugarRequest() {
        return new CrearLugarRequest("Estadio Municipal", "Calle 1 y 2", "Tandil",
                new BigDecimal("-37.321667"), new BigDecimal("-59.133333"));
    }
}
