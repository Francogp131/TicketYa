package ar.edu.unicen.ticketya.recommendation.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.unicen.ticketya.recommendation.dominio.HistorialCompra;
import ar.edu.unicen.ticketya.recommendation.dominio.Preferencia;
import ar.edu.unicen.ticketya.recommendation.evento.OrdenPagadaEvent;
import ar.edu.unicen.ticketya.recommendation.evento.OrdenPagadaPayload;
import ar.edu.unicen.ticketya.recommendation.repositorio.PreferenciaRepository;
import ar.edu.unicen.ticketya.recommendation.servicio.HistorialService;
import ar.edu.unicen.ticketya.recommendation.servicio.PreferenceService;
import ar.edu.unicen.ticketya.recommendation.web.dto.PreferenciasRequest;
import ar.edu.unicen.ticketya.recommendation.web.dto.PreferenciasResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifica contra MongoDB real (Testcontainers) los índices de la tarea 7.1,
 * el seed idempotente y el upsert de preferencias de la tarea 7.2.
 * Se omite automáticamente si Docker no está corriendo.
 */
@SpringBootTest(properties = {
        "recommendation.seed.enabled=true",
        "recommendation.mongo.ensure-indexes=true",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.dynamic=false"
})
@Testcontainers(disabledWithoutDocker = true)
class RecommendationMongoIntegrationTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
    }

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private MongoIndexInitializer indexInitializer;

    @Autowired
    private RecommendationSeedRunner seedRunner;

    @Autowired
    private PreferenceService preferenceService;

    @Autowired
    private PreferenciaRepository preferenciaRepository;

    @Autowired
    private HistorialService historialService;

    @Test
    void creaLosIndicesDeclaradosYRepetirlosEsIdempotente() {
        indexInitializer.crearIndices();
        indexInitializer.crearIndices();

        assertThat(nombresDeIndices("preferencias"))
                .contains("uk_preferencias_cliente", "idx_preferencias_ciudad");
        assertThat(nombresDeIndices("historial_compras"))
                .contains("uk_historial_evento_cliente_evento",
                        "idx_historial_cliente_fecha",
                        "idx_historial_evento_cliente");
        assertThat(nombresDeIndices("eventos_vistos"))
                .contains("idx_eventos_vistos_ciudad_tipo", "idx_eventos_vistos_fecha");

        IndexInfo unico = mongoTemplate.indexOps("preferencias").getIndexInfo().stream()
                .filter(index -> index.getName().equals("uk_preferencias_cliente"))
                .findFirst()
                .orElseThrow();
        assertThat(unico.isUnique()).isTrue();
    }

    @Test
    void elSeedEsIdempotenteYNoDuplicaDocumentos() {
        long historialAntes = mongoTemplate.count(new Query(), "historial_compras");
        long eventosAntes = mongoTemplate.count(new Query(), "eventos_vistos");

        seedRunner.sembrar();
        seedRunner.sembrar();

        assertThat(mongoTemplate.count(new Query(), "historial_compras")).isEqualTo(historialAntes);
        assertThat(mongoTemplate.count(new Query(), "eventos_vistos")).isEqualTo(eventosAntes);
        assertThat(preferenciaRepository.findByClienteId("33333333-3333-4333-8333-333333333333"))
                .isPresent();
    }

    @Test
    void hayComoMaximoUnDocumentoDePreferenciasPorCliente() {
        UUID cliente = UUID.randomUUID();
        String clienteId = cliente.toString();

        PreferenciasResponse primera = preferenceService.guardar(cliente,
                new PreferenciasRequest(List.of("RECITAL"), List.of("Los Piojos"), "Tandil", null));
        PreferenciasResponse segunda = preferenceService.guardar(cliente,
                new PreferenciasRequest(List.of("TEATRO"), List.of("Compañía Teatral Tandil"),
                        "Olavarria", null));

        long documentos = mongoTemplate.count(
                Query.query(Criteria.where("clienteId").is(clienteId)), Preferencia.class);
        assertThat(documentos).isEqualTo(1);
        assertThat(primera.actualizadoEn()).isNotNull();
        assertThat(segunda.tipos()).containsExactly("TEATRO");
        assertThat(segunda.ciudad()).isEqualTo("Olavarria");

        Preferencia duplicado = new Preferencia();
        duplicado.setClienteId(clienteId);
        duplicado.setCiudad("Tandil");
        assertThatThrownBy(() -> mongoTemplate.insert(duplicado))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void registraOrdenPagadaUnaSolaVezPorEventoDeDominio() {
        String clienteId = UUID.randomUUID().toString();
        String eventoId = UUID.randomUUID().toString();
        String ordenId = UUID.randomUUID().toString();
        String eventId = UUID.randomUUID().toString();
        OrdenPagadaEvent event = new OrdenPagadaEvent(eventId, OrdenPagadaEvent.TIPO,
                Instant.parse("2026-11-15T12:00:00Z"), UUID.randomUUID().toString(), 1,
                new OrdenPagadaPayload(ordenId, clienteId, eventoId, "Festival", "FESTIVAL",
                        List.of("Los Piojos"), "Tandil", Instant.parse("2027-03-15T00:00:00Z"),
                        OrdenPagadaPayload.ESTADO_PAGADA));

        assertThat(historialService.registrar(event)).isTrue();
        assertThat(historialService.registrar(event)).isFalse();

        Query query = Query.query(Criteria.where("eventId").is(eventId)
                .and("clienteId").is(clienteId)
                .and("eventoId").is(eventoId));
        assertThat(mongoTemplate.count(query, HistorialCompra.class)).isEqualTo(1);
        HistorialCompra compra = mongoTemplate.findOne(query, HistorialCompra.class);
        assertThat(compra).isNotNull();
        assertThat(compra.getTipo()).isEqualTo("FESTIVAL");
        assertThat(compra.getOrdenId()).isEqualTo(ordenId);
    }

    @Test
    void unEventoReintegradoNoEntraAlHistorial() {
        String clienteId = UUID.randomUUID().toString();
        String eventoId = UUID.randomUUID().toString();
        String eventId = UUID.randomUUID().toString();
        OrdenPagadaEvent event = new OrdenPagadaEvent(eventId, "orden.reintegrada",
                Instant.parse("2026-11-15T12:00:00Z"), UUID.randomUUID().toString(), 1,
                new OrdenPagadaPayload(UUID.randomUUID().toString(), clienteId, eventoId,
                        "Festival", "FESTIVAL", List.of(), "Tandil",
                        Instant.parse("2027-03-15T00:00:00Z"), OrdenPagadaPayload.ESTADO_PAGADA));

        assertThat(historialService.registrar(event)).isFalse();
        assertThat(mongoTemplate.count(Query.query(Criteria.where("eventId").is(eventId)),
                HistorialCompra.class)).isZero();
    }

    private List<String> nombresDeIndices(String coleccion) {
        return mongoTemplate.indexOps(coleccion).getIndexInfo().stream()
                .map(IndexInfo::getName)
                .toList();
    }
}
