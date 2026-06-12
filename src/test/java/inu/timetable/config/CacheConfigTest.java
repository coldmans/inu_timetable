package inu.timetable.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;

import static org.assertj.core.api.Assertions.assertThat;

class CacheConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(CacheConfig.class)
            .withPropertyValues("subject.cache.expire-after-write=10m");

    @Test
    void usesCaffeineCacheManagerByDefault() {
        contextRunner.run(context -> {
            CacheManager cacheManager = context.getBean(CacheManager.class);

            assertThat(cacheManager).isInstanceOf(CaffeineCacheManager.class);
        });
    }
}
