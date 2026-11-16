package ar.edu.unicen.ticketya.catalog.servicio;

import ar.edu.unicen.ticketya.catalog.config.CacheConfig;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Service
public class CatalogCacheService {

    private static final Logger log = LoggerFactory.getLogger(CatalogCacheService.class);

    private final CacheManager cacheManager;

    public CatalogCacheService(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @SuppressWarnings("unchecked")
    public <T> CacheResult<T> getOrLoad(String cacheName, Object key, Supplier<T> loader) {
        Cache cache = cache(cacheName);
        if (cache == null) {
            return new CacheResult<>(loader.get(), "BYPASS");
        }
        try {
            Cache.ValueWrapper wrapper = cache.get(key);
            if (wrapper != null && wrapper.get() != null) {
                return new CacheResult<>((T) wrapper.get(), "HIT");
            }
        } catch (RuntimeException ex) {
            log.warn("Cache GET degradado cache={} key={}: {}", cacheName, key, ex.getMessage());
            return new CacheResult<>(loader.get(), "BYPASS");
        }
        T loaded = loader.get();
        try {
            cache.put(key, loaded);
        } catch (RuntimeException ex) {
            log.warn("Cache PUT degradado cache={} key={}: {}", cacheName, key, ex.getMessage());
        }
        return new CacheResult<>(loaded, "MISS");
    }

    public void evict(String cacheName, Object key) {
        Cache cache = cache(cacheName);
        if (cache == null) {
            return;
        }
        try {
            cache.evict(key);
        } catch (RuntimeException ex) {
            log.warn("Cache EVICT degradado cache={} key={}: {}", cacheName, key, ex.getMessage());
        }
    }

    public void clear(String cacheName) {
        Cache cache = cache(cacheName);
        if (cache == null) {
            return;
        }
        try {
            cache.clear();
        } catch (RuntimeException ex) {
            log.warn("Cache CLEAR degradado cache={}: {}", cacheName, ex.getMessage());
        }
    }

    public void clearSearches() {
        clear(CacheConfig.EVENT_SEARCH);
    }

    public void evictEventCaches(java.util.UUID eventoId) {
        evict(CacheConfig.EVENT_DETAIL_STATIC, eventoId);
        clearSearches();
    }

    private Cache cache(String name) {
        return cacheManager.getCache(name);
    }

    public record CacheResult<T>(T value, String header) {}
}