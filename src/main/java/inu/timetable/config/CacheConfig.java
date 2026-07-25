package inu.timetable.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import inu.timetable.service.SubjectCacheNames;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(
            @Value("${subject.cache.maximum-size:1000}") long maximumSize,
            @Value("${subject.cache.expire-after-write:10m}") String expireAfterWrite) {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(SubjectCacheNames.ALL.toArray(String[]::new));
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(parseDuration(expireAfterWrite))
                .recordStats());
        return cacheManager;
    }

    private Duration parseDuration(String value) {
        return DurationStyle.detectAndParse(value);
    }
}
