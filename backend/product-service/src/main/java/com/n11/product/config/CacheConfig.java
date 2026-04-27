package com.n11.product.config;

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
public class CacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration base = baseConfig(Duration.ofMinutes(5));
        Map<String, RedisCacheConfiguration> perCache = Map.of(
                "categories", base.entryTtl(Duration.ofHours(1)),
                "products:byId", base.entryTtl(Duration.ofMinutes(5)),
                "products:bySlug", base.entryTtl(Duration.ofMinutes(5)),
                "products:autocomplete", base.entryTtl(Duration.ofMinutes(1))
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
        // PolymorphicTypeValidator scoped to our DTO package — prevents the
        // 'pickle-style' type-confusion gadgets that come with Jackson default-typing.
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
                        ObjectMapper.DefaultTyping.NON_FINAL);
        return new GenericJackson2JsonRedisSerializer(mapper);
    }
}
