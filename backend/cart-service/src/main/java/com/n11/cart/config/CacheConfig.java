package com.n11.cart.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.Map;

/**
 * Redis cache topology for cart-service.
 *
 * <p>Caches</p>
 * <ul>
 *   <li>{@code coupons:byCode}     60s — short TTL because the saga's
 *       reserveOne / releaseOne also evict on write, but TTL is the safety
 *       net for any path we forget to evict from.</li>
 *   <li>{@code campaigns:active}    60s — admin-tunable rules; 60s drift OK</li>
 * </ul>
 *
 * <p>Eviction model: every write that mutates a coupon's redemptions
 * counter ({@link com.n11.cart.repository.CouponRepository#reserveOne}
 * and {@link com.n11.cart.repository.CouponRepository#releaseOne}) carries
 * a matching {@code @CacheEvict}, so a reservation race that bumps the
 * counter past max_redemptions is reflected on the next quote without
 * waiting for TTL.</p>
 */
@Configuration
@EnableCaching
// Skip the entire Redis cache wiring when spring.cache.type=none (test profile).
// Production default is redis, so this stays active there.
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis", matchIfMissing = true)
public class CacheConfig {

    /**
     * Schema version baked into every cache key — bump when the cached value
     * shape changes (DTO field added/removed, serializer rewired) so old
     * entries orphan and TTL-evict instead of poisoning new readers.  See
     * {@code docs/caching.md} for the bump checklist.
     */
    @Value("${n11.cache.schema-version:1}")
    private String schemaVersion;

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration base = baseConfig(Duration.ofSeconds(60));
        Map<String, RedisCacheConfiguration> perCache = Map.of(
                "coupons:byCode", base.entryTtl(Duration.ofSeconds(60)),
                "campaigns:active", base.entryTtl(Duration.ofSeconds(60))
        );
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(base)
                .withInitialCacheConfigurations(perCache)
                .build();
    }

    private RedisCacheConfiguration baseConfig(Duration defaultTtl) {
        // Key shape: cart:v<schemaVersion>:<cacheName>::<key>
        // See product-service CacheConfig for the schema-version rationale.
        String prefix = "cart:v" + schemaVersion + ":";
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(defaultTtl)
                .disableCachingNullValues()
                .computePrefixWith(cacheName -> prefix + cacheName + "::")
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        jsonSerializer()));
    }

    private GenericJackson2JsonRedisSerializer jsonSerializer() {
        // See product-service CacheConfig for the full rationale.  Short
        // version: we need `@class` embedded as a property (not wrapper-array)
        // and applied to EVERYTHING so record DTOs round-trip; PTV restricts
        // the type names to our package + JDK value types.
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .activateDefaultTyping(
                        BasicPolymorphicTypeValidator.builder()
                                .allowIfBaseType(Object.class)
                                .allowIfSubType("com.n11.cart.")
                                .allowIfSubType("java.util.")
                                .allowIfSubType("java.time.")
                                .allowIfSubType("java.math.")
                                .build(),
                        ObjectMapper.DefaultTyping.EVERYTHING,
                        JsonTypeInfo.As.PROPERTY);
        return new GenericJackson2JsonRedisSerializer(mapper);
    }
}
