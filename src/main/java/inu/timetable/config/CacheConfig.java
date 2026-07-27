package inu.timetable.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.github.benmanes.caffeine.cache.Caffeine;
import inu.timetable.service.SubjectCacheNames;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Set;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    @ConditionalOnProperty(name = "subject.cache.provider", havingValue = "caffeine", matchIfMissing = true)
    public CacheManager caffeineCacheManager(
            @Value("${subject.cache.maximum-size:1000}") long maximumSize,
            @Value("${subject.cache.expire-after-write:10m}") String expireAfterWrite) {
        return createCaffeineCacheManager(maximumSize, expireAfterWrite);
    }

    private CaffeineCacheManager createCaffeineCacheManager(
            long maximumSize,
            String expireAfterWrite) {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(SubjectCacheNames.ALL.toArray(String[]::new));
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(parseDuration(expireAfterWrite))
                .recordStats());
        return cacheManager;
    }

    @Bean
    @ConditionalOnExpression(
            "'${subject.cache.provider:caffeine}' == 'redis' or "
                    + "'${subject.cache.provider:caffeine}' == 'two-level'")
    public RedisSerializer<Object> subjectCacheValueSerializer(ObjectMapper objectMapper) {
        ObjectMapper cacheObjectMapper = objectMapper.copy();
        cacheObjectMapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType("inu.timetable.")
                        .allowIfSubType("java.lang.")
                        .allowIfSubType("java.util.")
                        .build(),
                ObjectMapper.DefaultTyping.EVERYTHING,
                JsonTypeInfo.As.PROPERTY);
        return new GenericJackson2JsonRedisSerializer(cacheObjectMapper);
    }

    @Bean
    @ConditionalOnProperty(name = "subject.cache.provider", havingValue = "redis")
    public CacheManager redisCacheManager(
            RedisConnectionFactory connectionFactory,
            RedisSerializer<Object> subjectCacheValueSerializer,
            MeterRegistry meterRegistry,
            @Value("${subject.cache.expire-after-write:10m}") String expireAfterWrite,
            @Value("${subject.cache.redis.key-prefix:inu:timetable:dev}") String keyPrefix,
            @Value("${subject.cache.redis.retry-after:5s}") String retryAfter) {
        return createRedisCacheManager(
                connectionFactory,
                subjectCacheValueSerializer,
                meterRegistry,
                expireAfterWrite,
                keyPrefix,
                retryAfter);
    }

    @Bean
    @ConditionalOnProperty(name = "subject.cache.provider", havingValue = "two-level")
    public CacheManager twoLevelCacheManager(
            RedisConnectionFactory connectionFactory,
            RedisSerializer<Object> subjectCacheValueSerializer,
            MeterRegistry meterRegistry,
            @Value("${subject.cache.maximum-size:1000}") long maximumSize,
            @Value("${subject.cache.expire-after-write:10m}") String expireAfterWrite,
            @Value("${subject.cache.redis.key-prefix:inu:timetable:dev}") String keyPrefix,
            @Value("${subject.cache.redis.retry-after:5s}") String retryAfter) {
        CaffeineCacheManager local = createCaffeineCacheManager(maximumSize, expireAfterWrite);
        CacheManager shared = createRedisCacheManager(
                connectionFactory,
                subjectCacheValueSerializer,
                meterRegistry,
                expireAfterWrite,
                keyPrefix,
                retryAfter);
        return new TwoLevelCacheManager(local, shared, meterRegistry);
    }

    private CacheManager createRedisCacheManager(
            RedisConnectionFactory connectionFactory,
            RedisSerializer<Object> subjectCacheValueSerializer,
            MeterRegistry meterRegistry,
            String expireAfterWrite,
            String keyPrefix,
            String retryAfter) {
        RedisCacheConfiguration cacheConfiguration = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(parseDuration(expireAfterWrite))
                .disableCachingNullValues()
                .prefixCacheNameWith(normalizePrefix(keyPrefix))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        subjectCacheValueSerializer));

        // Cache values are safely reproducible from PostgreSQL. A cache-wide Redis lock would
        // add an EXISTS round trip to every hit; duplicate computation on a rare miss is cheaper.
        RedisCacheWriter cacheWriter = RedisCacheWriter.nonLockingRedisCacheWriter(
                connectionFactory,
                BatchStrategies.scan(1_000));
        RedisCacheManager delegate = RedisCacheManager.builder(cacheWriter)
                .cacheDefaults(cacheConfiguration)
                .initialCacheNames(Set.copyOf(SubjectCacheNames.ALL))
                .disableCreateOnMissingCache()
                .enableStatistics()
                .build();
        // The delegate is wrapped below, so it is not a Spring bean and must be initialized here.
        delegate.afterPropertiesSet();

        return new ResilientRedisCacheManager(
                delegate,
                parseDuration(retryAfter),
                meterRegistry);
    }

    private Duration parseDuration(String value) {
        return DurationStyle.detectAndParse(value);
    }

    private String normalizePrefix(String prefix) {
        String normalized = prefix == null ? "" : prefix.trim();
        return normalized.endsWith(":") ? normalized : normalized + ":";
    }
}
