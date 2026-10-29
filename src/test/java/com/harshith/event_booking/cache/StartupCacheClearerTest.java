package com.harshith.event_booking.cache;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StartupCacheClearerTest {

    @Test
    void clearsEveryEventCache() {
        CacheManager manager = mock(CacheManager.class);
        Cache pages = cache(manager, CacheNames.EVENT_PAGES);
        Cache events = cache(manager, CacheNames.EVENTS);
        Cache seatMaps = cache(manager, CacheNames.SEAT_MAPS);

        new StartupCacheClearer(manager).clearEventCaches();

        verify(pages).clear();
        verify(events).clear();
        verify(seatMaps).clear();
    }

    @Test
    void redisFailure_doesNotStopStartup_andTheOtherCachesAreStillCleared() {
        CacheManager manager = mock(CacheManager.class);
        Cache pages = cache(manager, CacheNames.EVENT_PAGES);
        Cache events = cache(manager, CacheNames.EVENTS);
        Cache seatMaps = cache(manager, CacheNames.SEAT_MAPS);
        doThrow(new IllegalStateException("Redis is down")).when(pages).clear();

        assertThatCode(() -> new StartupCacheClearer(manager).clearEventCaches()).doesNotThrowAnyException();

        verify(events).clear();
        verify(seatMaps).clear();
    }

    private static Cache cache(CacheManager manager, String name) {
        Cache cache = mock(Cache.class);
        when(manager.getCache(name)).thenReturn(cache);
        return cache;
    }
}
