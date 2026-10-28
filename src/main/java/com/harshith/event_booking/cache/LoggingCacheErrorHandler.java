package com.harshith.event_booking.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

/**
 * Keeps requests working when Redis is down. Spring calls this instead of throwing:
 * a failed get becomes a cache miss (the method runs and reads the database),
 * and a failed put or evict is skipped. Each failure is logged as one warning line.
 */
public class LoggingCacheErrorHandler implements CacheErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(LoggingCacheErrorHandler.class);

    @Override
    public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
        warn("read", cache, key, e, "treating it as a cache miss and reading the database");
    }

    @Override
    public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
        warn("write", cache, key, e, "skipped; the response was still served from the database");
    }

    @Override
    public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
        warn("evict", cache, key, e, "skipped; any stale entry expires with its TTL");
    }

    @Override
    public void handleCacheClearError(RuntimeException e, Cache cache) {
        warn("clear", cache, "*", e, "skipped; entries expire with their TTL");
    }

    private static void warn(String action, Cache cache, Object key, RuntimeException e, String outcome) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        log.warn("Cache {} failed for {}::{}: {} ({}: {})",
                action, cache.getName(), key, outcome, root.getClass().getSimpleName(), root.getMessage());
    }
}
