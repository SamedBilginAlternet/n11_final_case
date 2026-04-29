package com.n11.product.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Redis cache topology for product-service.
 *
 * <p>Caches</p>
 * <ul>
 *   <li>{@code categories}     1h — almost never changes, cheap to over-cache</li>
 *   <li>{@code products:byId}  5m — price/stock can drift, 5m is a fair window</li>
 *   <li>{@code products:bySlug} 5m — same as byId, different lookup key</li>
 *   <li>{@code products:autocomplete} 1m — feels live, drops repeated typeahead RTTs</li>
 * </ul>
 *
 * <p>Search results ({@link ProductController#list}) are intentionally NOT
 * cached — too many filter combinations (categoryId, slug, q, page, sort) to
 * make the hit rate worth the memory.</p>
 *
 * <p>Serialization: keys as plain strings (debuggable in {@code redis-cli}),
 * values as JSON via {@link GenericJackson2JsonRedisSerializer}. JDK binary
 * serialization is the Redis default and is both fragile across class
 * renames and unreadable in the wire dump — JSON wins on every axis here.</p>
 */
@Configuration
@EnableCaching
// Skip the entire Redis cache wiring when running with spring.cache.type=none
// (e.g. integration tests that don't want a Redis container). Production keeps
// the default 'redis' value, so this is a no-op there.
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis", matchIfMissing = true)
public class CacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration base = baseConfig(Duration.ofMinutes(5));
        Map<String, RedisCacheConfiguration> perCache = new LinkedHashMap<>();
        perCache.put("categories", base.entryTtl(Duration.ofHours(1)));
        perCache.put("products:byId", base.entryTtl(Duration.ofMinutes(5)));
        perCache.put("products:bySlug", base.entryTtl(Duration.ofMinutes(5)));
        perCache.put("products:autocomplete", base.entryTtl(Duration.ofMinutes(1)));
        // Recommendation strip: 5 minute window is short enough that price /
        // stock changes propagate to the AI explanations on the next miss,
        // long enough that a popular product page hits redis 99% of the time.
        perCache.put("recommendations", base.entryTtl(Duration.ofMinutes(5)));
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(base)
                .withInitialCacheConfigurations(perCache)
                .build();
    }

    private RedisCacheConfiguration baseConfig(Duration defaultTtl) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(defaultTtl)
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        jsonSerializer()));
    }

    private GenericJackson2JsonRedisSerializer jsonSerializer() {
        // We need `@class` type info embedded in cached JSON so reads can
        // reconstruct the concrete DTO type (cache values are Object-typed,
        // and our DTOs are records — final classes that DefaultTyping.NON_FINAL
        // would skip).  The two knobs that matter:
        //   - As.PROPERTY  → adds {"@class":"...","field":...}.  Avoid
        //     WRAPPER_ARRAY: it omits the marker for final types on write but
        //     demands it on read, blowing up with `expected START_ARRAY`.
        //   - DefaultTyping.EVERYTHING  → applies to records too.  NON_FINAL
        //     skips final types, leaves records without `@class`, and reads
        //     come back as LinkedHashMap → ClassCastException.
        // PolymorphicTypeValidator restricts the embedded class names to our
        // DTO package + JDK value types — keeps the door shut on Jackson
        // default-typing gadget chains.
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .activateDefaultTyping(
                        BasicPolymorphicTypeValidator.builder()
                                .allowIfBaseType(Object.class)
                                .allowIfSubType("com.n11.product.")
                                .allowIfSubType("java.util.")
                                .allowIfSubType("java.time.")
                                .allowIfSubType("java.math.")
                                .build(),
                        ObjectMapper.DefaultTyping.EVERYTHING,
                        JsonTypeInfo.As.PROPERTY);
        return new GenericJackson2JsonRedisSerializer(mapper);
    }
}
