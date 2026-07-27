package inu.timetable.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TwoLevelCacheManagerTest {

    @Test
    void promotesSharedValueToLocalCacheAndUsesLocalOnNextRead() {
        ConcurrentMapCacheManager local = new ConcurrentMapCacheManager("subjects");
        ConcurrentMapCacheManager shared = new ConcurrentMapCacheManager("subjects");
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        Cache sharedCache = shared.getCache("subjects");
        assertThat(sharedCache).isNotNull();
        sharedCache.put("key", "shared-value");
        Cache cache = new TwoLevelCacheManager(local, shared, meterRegistry).getCache("subjects");

        assertThat(cache).isNotNull();
        assertThat(cache.get("key", String.class)).isEqualTo("shared-value");
        sharedCache.put("key", "changed-after-promotion");
        assertThat(cache.get("key", String.class)).isEqualTo("shared-value");

        assertThat(meterRegistry.counter(
                "subject.cache.l1.requests",
                "cache", "subjects",
                "result", "miss").count()).isEqualTo(1);
        assertThat(meterRegistry.counter(
                "subject.cache.l1.requests",
                "cache", "subjects",
                "result", "hit").count()).isEqualTo(1);
    }

    @Test
    void loadsDatabaseOnceAndPopulatesBothLevels() {
        ConcurrentMapCacheManager local = new ConcurrentMapCacheManager("subjects");
        ConcurrentMapCacheManager shared = new ConcurrentMapCacheManager("subjects");
        Cache cache = new TwoLevelCacheManager(
                local,
                shared,
                new SimpleMeterRegistry()).getCache("subjects");
        AtomicInteger loads = new AtomicInteger();

        assertThat(cache).isNotNull();
        assertThat(cache.get("key", () -> {
            loads.incrementAndGet();
            return "database-value";
        })).isEqualTo("database-value");
        assertThat(cache.get("key", () -> {
            loads.incrementAndGet();
            return "unexpected";
        })).isEqualTo("database-value");

        assertThat(loads).hasValue(1);
        assertThat(shared.getCache("subjects").get("key", String.class))
                .isEqualTo("database-value");
    }

    @Test
    void clearInvalidatesLocalAndSharedLevels() {
        ConcurrentMapCacheManager local = new ConcurrentMapCacheManager("subjects");
        ConcurrentMapCacheManager shared = new ConcurrentMapCacheManager("subjects");
        Cache cache = new TwoLevelCacheManager(
                local,
                shared,
                new SimpleMeterRegistry()).getCache("subjects");

        assertThat(cache).isNotNull();
        cache.put("key", "value");
        cache.clear();

        assertThat(local.getCache("subjects").get("key")).isNull();
        assertThat(shared.getCache("subjects").get("key")).isNull();
    }
}
