package ar.edu.unicen.ticketya.catalog.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.unicen.ticketya.catalog.config.CacheConfig;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.SimpleValueWrapper;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

@ExtendWith(MockitoExtension.class)
class CatalogCacheServiceTest {

    @Mock
    private CacheManager cacheManager;
    @Mock
    private Cache cache;

    private CatalogCacheService cacheService;

    @BeforeEach
    void setUp() {
        cacheService = new CatalogCacheService(cacheManager);
    }

    @Test
    void unHitDevuelveElValorSinCargarLaBase() {
        when(cacheManager.getCache("detalle")).thenReturn(cache);
        when(cache.get("clave")).thenReturn(new SimpleValueWrapper("cacheado"));

        CatalogCacheService.CacheResult<String> result =
                cacheService.getOrLoad("detalle", "clave", () -> "base");

        assertThat(result.value()).isEqualTo("cacheado");
        assertThat(result.header()).isEqualTo("HIT");
    }

    @Test
    void unMissCargaLaBaseYGuardaElValor() {
        when(cacheManager.getCache("detalle")).thenReturn(cache);
        when(cache.get("clave")).thenReturn(null);

        CatalogCacheService.CacheResult<String> result =
                cacheService.getOrLoad("detalle", "clave", () -> "base");

        assertThat(result.value()).isEqualTo("base");
        assertThat(result.header()).isEqualTo("MISS");
        verify(cache).put("clave", "base");
    }

    @Test
    void siNoHayCacheConfiguradaDegradaALaBase() {
        when(cacheManager.getCache("detalle")).thenReturn(null);

        CatalogCacheService.CacheResult<String> result =
                cacheService.getOrLoad("detalle", "clave", () -> "base");

        assertThat(result.value()).isEqualTo("base");
        assertThat(result.header()).isEqualTo("BYPASS");
    }

    @Test
    void siRedisFallaEnLecturaDegradaALaBase() {
        when(cacheManager.getCache("detalle")).thenReturn(cache);
        when(cache.get("clave")).thenThrow(new IllegalStateException("redis caido"));

        CatalogCacheService.CacheResult<String> result =
                cacheService.getOrLoad("detalle", "clave", () -> "base");

        assertThat(result.value()).isEqualTo("base");
        assertThat(result.header()).isEqualTo("BYPASS");
    }

    @Test
    void siRedisFallaAlInvalidarNoRompeLaOperacion() {
        UUID eventoId = UUID.randomUUID();
        when(cacheManager.getCache("event-detail-static")).thenReturn(cache);
        when(cacheManager.getCache("event-search")).thenReturn(cache);

        org.mockito.Mockito.doThrow(new IllegalStateException("redis caido"))
                .when(cache).evict(eventoId);
        org.mockito.Mockito.doThrow(new IllegalStateException("redis caido"))
                .when(cache).clear();

        cacheService.evictEventCaches(eventoId);

        verify(cache).evict(eventoId);
        verify(cache).clear();
    }

    @Test
    void siRedisFallaAlEscribirDevuelveElValorDeLaBase() {
        when(cacheManager.getCache("detalle")).thenReturn(cache);
        when(cache.get(any())).thenReturn(null);
        org.mockito.Mockito.doThrow(new IllegalStateException("redis caido"))
                .when(cache).put(any(), any());

        CatalogCacheService.CacheResult<String> result =
                cacheService.getOrLoad("detalle", "clave", () -> "base");

        assertThat(result.value()).isEqualTo("base");
        assertThat(result.header()).isEqualTo("MISS");
    }

    @Test
    void configuraTtlsDistintosPorTipoDeCache() {
        RedisCacheManager manager = new CacheConfig().cacheManager(mock(RedisConnectionFactory.class),
                Duration.ofMinutes(10), Duration.ofMinutes(2), Duration.ofSeconds(30));
        manager.afterPropertiesSet();

        assertThat(((RedisCache) manager.getCache(CacheConfig.VENUES)).getCacheConfiguration().getTtl())
                .isEqualTo(Duration.ofMinutes(10));
        assertThat(((RedisCache) manager.getCache(CacheConfig.EVENT_DETAIL_STATIC)).getCacheConfiguration()
                .getTtl()).isEqualTo(Duration.ofMinutes(2));
        assertThat(((RedisCache) manager.getCache(CacheConfig.EVENT_SEARCH)).getCacheConfiguration().getTtl())
                .isEqualTo(Duration.ofSeconds(30));
    }
}
