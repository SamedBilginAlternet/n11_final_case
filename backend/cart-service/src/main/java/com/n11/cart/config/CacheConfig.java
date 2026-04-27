package com.n11.cart.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
public class CacheConfig {

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
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(defaultTtl)
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        jsonSerializer()));
    }

    private GenericJackson2JsonRedisSerializer jsonSerializer() {
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
                        ObjectMapper.DefaultTyping.NON_FINAL);
        return new GenericJackson2JsonRedisSerializer(mapper);
    }
}
