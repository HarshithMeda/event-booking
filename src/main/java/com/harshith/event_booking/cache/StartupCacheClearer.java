package com.harshith.event_booking.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Clears the event caches when the app starts. A new release can bring new data (for example a Flyway
 * migration that adds events), and Redis keeps entries across app restarts, so without this the old
 * event list could be served for up to its TTL.
 *
 * Redis being down must not stop the app from starting, so failures are logged and skipped.
 */
@Component
public class StartupCacheClearer {

    static final List<String> CACHES = List.of(CacheNames.EVENT_PAGES, CacheNames.EVENTS, CacheNames.SEAT_MAPS);

    private static final Logger log = LoggerFactory.getLogger(StartupCacheClearer.class);

    private final CacheManager cacheManager;

    public StartupCacheClearer(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void clearEventCaches() {
        List<String> cleared = new ArrayList<>();
        for (String name : CACHES) {
            try {
                Cache cache = cacheManager.getCache(name);
                if (cache != null) {
                    cache.clear();
                    cleared.add(name);
                }
            } catch (RuntimeException e) {
                log.warn("Could not clear cache {} on startup; its entries will expire with their TTL ({})",
                        name, e.getClass().getSimpleName());
            }
        }
        if (!cleared.isEmpty()) {
            log.info("Cleared caches on startup: {}", cleared);
        }
    }
}
