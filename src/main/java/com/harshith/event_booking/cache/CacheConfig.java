package com.harshith.event_booking.cache;

import com.harshith.event_booking.dto.EventResponse;
import com.harshith.event_booking.dto.PageResponse;
import com.harshith.event_booking.dto.SeatResponse;
import io.lettuce.core.ClientOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.TypeFactory;

import java.time.Duration;
import java.util.List;

/**
 * Redis caching for the public read endpoints.
 * The cache is only for display. Booking logic always reads seats from the database.
 */
@Configuration
// Cache check runs before @Transactional, so a cache hit never takes a database connection.
@EnableCaching(order = Ordered.HIGHEST_PRECEDENCE)
public class CacheConfig implements CachingConfigurer {

    private final Duration eventTtl;
    private final Duration seatMapTtl;

    public CacheConfig(@Value("${app.cache.event-ttl:PT10M}") Duration eventTtl,
                       @Value("${app.cache.seat-map-ttl:PT30S}") Duration seatMapTtl) {
        this.eventTtl = eventTtl;
        this.seatMapTtl = seatMapTtl;
    }

    /** Each cache stores JSON of one exact type, with its own TTL (time to live). */
    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheCustomizer(RedisConnectionFactory connectionFactory) {
        JsonMapper mapper = JsonMapper.builder().build();
        TypeFactory types = mapper.getTypeFactory();
        JavaType eventPage = types.constructParametricType(PageResponse.class, EventResponse.class);
        JavaType event = types.constructType(EventResponse.class);
        JavaType seatMap = types.constructCollectionType(List.class, SeatResponse.class);
        return builder -> builder
                // Spring Data Redis 4 writes asynchronously by default: puts and evicts return before Redis
                // has done them, and their failures are dropped without reaching the error handler.
                // Immediate writes mean a seat map eviction is finished before the booking response is sent,
                // and a failed write or evict is logged as a warning.
                .cacheWriter(RedisCacheWriter.create(connectionFactory, writer -> writer.immediateWrites()))
                .withCacheConfiguration(CacheNames.EVENT_PAGES, config(mapper, eventPage, eventTtl))
                .withCacheConfiguration(CacheNames.EVENTS, config(mapper, event, eventTtl))
                .withCacheConfiguration(CacheNames.SEAT_MAPS, config(mapper, seatMap, seatMapTtl));
    }

    private static RedisCacheConfiguration config(JsonMapper mapper, JavaType type, Duration ttl) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .serializeValuesWith(SerializationPair.fromSerializer(new JacksonJsonRedisSerializer<>(mapper, type)));
    }

    /** While Redis is unreachable, fail cache calls at once instead of queueing them until they time out. */
    @Bean
    public LettuceClientConfigurationBuilderCustomizer failFastWhenRedisIsDown() {
        return builder -> builder.clientOptions(ClientOptions.builder()
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .build());
    }

    /** If Redis fails, log a warning and carry on: a failed read counts as a cache miss and goes to the database. */
    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }
}
