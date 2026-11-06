package ar.edu.unicen.ticketya.orders.servicio;

import ar.edu.unicen.ticketya.orders.dominio.IdempotencyKey;
import ar.edu.unicen.ticketya.orders.repositorio.IdempotencyKeyRepository;
import ar.edu.unicen.ticketya.orders.web.ApiException;
import ar.edu.unicen.ticketya.orders.web.dto.OrderResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotencia de POST /api/orders (tarea 4.6).
 *
 * <p>La clave se reclama en una transacción propia (REQUIRES_NEW) para que dos
 * requests concurrentes no creen dos órdenes: la segunda choca con la PK y
 * lee el estado de la primera. Al terminar la compra se guarda la respuesta
 * completa, de modo que un reintento devuelve exactamente lo mismo.</p>
 */
@Service
public class IdempotencyService {

    private static final int HORAS_VIGENCIA = 24;

    private final IdempotencyKeyRepository repository;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    public IdempotencyService(IdempotencyKeyRepository repository, ObjectMapper objectMapper,
                              JdbcTemplate jdbcTemplate) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
        // Conserva el offset argentino al guardar y recuperar la respuesta idempotente.
        this.objectMapper = objectMapper.copy()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.WRITE_DATES_WITH_CONTEXT_TIME_ZONE)
                .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
    }

    /**
     * Registra la clave. Si ya existe, lanza DataIntegrityViolationException y
     * {@link OrderService} resuelve el caso leyendo la fila existente.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(String key, String requestHash, OffsetDateTime momento) {
        // INSERT directo: con @Id asignado, repository.save() haría merge y
        // pisaría la fila existente en lugar de detectar la clave duplicada.
        jdbcTemplate.update("""
                INSERT INTO idempotency_key (key, request_hash, estado, expires_at)
                VALUES (?, ?, 'EN_PROCESO', ?)
                """, key, requestHash, Timestamp.from(momento.plusHours(HORAS_VIGENCIA).toInstant()));
    }

    /**
     * Resuelve un reintento: misma respuesta si el cuerpo es igual, 422 si la
     * clave se reutilizó con otro cuerpo y 409 mientras la compra original corre.
     */
    @Transactional(readOnly = true)
    public OrderResponse resolverExistente(String key, String requestHash) {
        IdempotencyKey existente = repository.findById(key)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_IN_PROGRESS",
                        "La compra con esta Idempotency-Key todavía está en proceso",
                        java.util.List.of(), Map.of("Retry-After", "1")));
        if (!existente.getRequestHash().equals(requestHash)) {
            throw ApiException.unprocessable("IDEMPOTENCY_KEY_REUSED",
                    "La Idempotency-Key ya se usó con un cuerpo distinto");
        }
        if ("COMPLETADA".equals(existente.getEstado()) && existente.getResponseBody() != null) {
            try {
                return objectMapper.readValue(existente.getResponseBody(), OrderResponse.class);
            } catch (JsonProcessingException ex) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                        "No se pudo recuperar la respuesta idempotente");
            }
        }
        throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_IN_PROGRESS",
                "La compra con esta Idempotency-Key todavía está en proceso",
                java.util.List.of(), Map.of("Retry-After", "1"));
    }

    /**
     * Guarda la respuesta para los reintentos. Participa de la transacción de
     * la compra para que la fila y la orden se confirmen juntas.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void completar(String key, OrderResponse response) {
        repository.findById(key).ifPresent(fila -> {
            fila.setEstado("COMPLETADA");
            fila.setResponseStatus(201);
            try {
                fila.setResponseBody(objectMapper.writeValueAsString(response));
            } catch (JsonProcessingException ex) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                        "No se pudo serializar la respuesta idempotente");
            }
            repository.save(fila);
        });
    }

    /**
     * Libera el reclamo cuando la compra falla, para que el cliente pueda
     * reintentar con la misma clave. Corre aparte porque la transacción
     * principal puede quedar en rollback-only.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void liberar(String key) {
        repository.deleteById(key);
    }

    public String hash(String reservaId, String medioPago, String token) {
        String contenido = reservaId + "|" + medioPago + "|" + token;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(contenido.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }
}
