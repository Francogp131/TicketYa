package ar.edu.unicen.ticketya.catalog.servicio;

import ar.edu.unicen.ticketya.catalog.dominio.Asiento;
import ar.edu.unicen.ticketya.catalog.dominio.Lugar;
import ar.edu.unicen.ticketya.catalog.dominio.Sector;
import ar.edu.unicen.ticketya.catalog.dominio.TipoSector;
import ar.edu.unicen.ticketya.catalog.repositorio.AsientoRepository;
import ar.edu.unicen.ticketya.catalog.repositorio.LugarRepository;
import ar.edu.unicen.ticketya.catalog.repositorio.SectorRepository;
import ar.edu.unicen.ticketya.catalog.web.ApiException;
import ar.edu.unicen.ticketya.catalog.web.dto.CrearLugarRequest;
import ar.edu.unicen.ticketya.catalog.web.dto.CrearSectorRequest;
import ar.edu.unicen.ticketya.catalog.web.dto.LugarResponse;
import ar.edu.unicen.ticketya.catalog.web.dto.PageResponse;
import ar.edu.unicen.ticketya.catalog.web.dto.SectorResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VenueService {

    private static final int CAPACIDAD_MAXIMA = 20000;
    private static final int FILAS_MAXIMAS = 100;
    private static final int ASIENTOS_POR_FILA_MAXIMOS = 200;

    private final LugarRepository lugarRepository;
    private final SectorRepository sectorRepository;
    private final AsientoRepository asientoRepository;

    public VenueService(LugarRepository lugarRepository,
                        SectorRepository sectorRepository,
                        AsientoRepository asientoRepository) {
        this.lugarRepository = lugarRepository;
        this.sectorRepository = sectorRepository;
        this.asientoRepository = asientoRepository;
    }

    @Transactional
    public LugarResponse crearLugar(CrearLugarRequest request) {
        Lugar lugar = new Lugar();
        lugar.setNombre(request.nombre().trim());
        lugar.setDireccion(request.direccion().trim());
        lugar.setCiudad(request.ciudad().trim());
        lugar.setLatitud(request.latitud());
        lugar.setLongitud(request.longitud());
        return LugarResponse.from(lugarRepository.save(lugar));
    }

    @Transactional(readOnly = true)
    public PageResponse<LugarResponse> listarLugares(int page, int size) {
        Page<Lugar> resultado = lugarRepository.findAll(
                PageRequest.of(normalizarPage(page), normalizarSize(size), Sort.by("nombre").ascending()));
        List<LugarResponse> contenido = resultado.getContent().stream().map(LugarResponse::from).toList();
        return new PageResponse<>(contenido, resultado.getNumber(), resultado.getSize(),
                resultado.getTotalElements(), resultado.getTotalPages());
    }

    @Transactional(readOnly = true)
    public LugarResponse obtenerLugar(UUID lugarId) {
        return LugarResponse.from(buscarLugar(lugarId));
    }

    @Transactional
    public SectorResponse crearSector(UUID lugarId, CrearSectorRequest request) {
        Lugar lugar = buscarLugar(lugarId);
        String nombre = request.nombre().trim();
        if (sectorRepository.existsByLugarIdAndNombre(lugar.getId(), nombre)) {
            throw ApiException.conflict("SECTOR_ALREADY_EXISTS",
                    "Ya existe un sector con el nombre '" + nombre + "' en el lugar " + lugarId);
        }

        Sector sector = new Sector();
        sector.setLugarId(lugar.getId());
        sector.setNombre(nombre);
        sector.setTipo(request.tipo());

        List<Asiento> asientos = new ArrayList<>();
        if (request.tipo() == TipoSector.NUMERADO) {
            if (request.filas() == null || request.filas() < 1
                    || request.asientosPorFila() == null || request.asientosPorFila() < 1) {
                throw validacion("filas", "un sector numerado requiere filas y asientosPorFila mayores a 0");
            }
            if (request.filas() > FILAS_MAXIMAS || request.asientosPorFila() > ASIENTOS_POR_FILA_MAXIMOS
                    || (long) request.filas() * request.asientosPorFila() > CAPACIDAD_MAXIMA) {
                throw validacion("filas", "el sector supera el máximo de " + CAPACIDAD_MAXIMA
                        + " asientos (" + FILAS_MAXIMAS + " filas x " + ASIENTOS_POR_FILA_MAXIMOS + " asientos)");
            }
            int capacidad = request.filas() * request.asientosPorFila();
            sector.setCapacidad(capacidad);
            sector.setFilas(request.filas());
            sector.setAsientosPorFila(request.asientosPorFila());
            sector = sectorRepository.save(sector);
            for (int fila = 1; fila <= request.filas(); fila++) {
                for (int numero = 1; numero <= request.asientosPorFila(); numero++) {
                    asientos.add(new Asiento(sector.getId(), fila, numero));
                }
            }
            asientoRepository.saveAll(asientos);
        } else {
            if (request.capacidad() == null || request.capacidad() < 1) {
                throw validacion("capacidad", "un sector no numerado requiere capacidad mayor a 0");
            }
            if (request.capacidad() > CAPACIDAD_MAXIMA) {
                throw validacion("capacidad", "la capacidad no puede superar " + CAPACIDAD_MAXIMA);
            }
            sector.setCapacidad(request.capacidad());
            sector.setFilas(null);
            sector.setAsientosPorFila(null);
            sector = sectorRepository.save(sector);
        }

        return SectorResponse.from(sector, asientos.size());
    }

    @Transactional(readOnly = true)
    public List<SectorResponse> listarSectores(UUID lugarId) {
        buscarLugar(lugarId);
        return sectorRepository.findByLugarIdOrderByNombreAsc(lugarId).stream()
                .map(sector -> SectorResponse.from(sector, asientoRepository.countBySectorId(sector.getId())))
                .toList();
    }

    public Lugar buscarLugar(UUID lugarId) {
        return lugarRepository.findById(lugarId)
                .orElseThrow(() -> ApiException.notFound("VENUE_NOT_FOUND", "No existe el lugar " + lugarId));
    }

    private ApiException validacion(String field, String message) {
        return new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "La solicitud tiene campos inválidos", List.of(Map.of("field", field, "message", message)));
    }

    private int normalizarPage(int page) {
        return Math.max(page, 0);
    }

    private int normalizarSize(int size) {
        if (size < 1) {
            return 20;
        }
        return Math.min(size, 100);
    }
}
