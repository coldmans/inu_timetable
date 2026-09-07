package inu.timetable.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.SimpleValueWrapper;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class ResilientRedisCacheManagerTest {

    @Test
    void fallsBackToLoaderAndBypassesRedisDuringCooldown() {
        AtomicLong nanoTime = new AtomicLong();
        TestCache target = new TestCache();
        target.failReads = true;
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        Cache cache = manager(target, nanoTime, meterRegistry).getCache("subjectFilters");
        AtomicInteger loads = new AtomicInteger();

        assertThat(cache).isNotNull();
        assertThat(cache.get("criteria", () -> {
            loads.incrementAndGet();
            return "database-value";
        })).isEqualTo("database-value");
        assertThat(cache.get("criteria", () -> {
            loads.incrementAndGet();
            return "database-value-2";
        })).isEqualTo("database-value-2");

        assertThat(loads).hasValue(2);
        assertThat(target.readAttempts).hasValue(1);
        assertThat(meterRegistry.counter("subject.cache.redis.failures").count()).isEqualTo(1);
        assertThat(meterRegistry.counter(
                "subject.cache.requests",
                "cache", "subjectFilters",
                "result", "bypass").count()).isEqualTo(2);
    }

    @Test
    void flushesPotentiallyStaleValuesBeforeUsingRedisAgain() {
        AtomicLong nanoTime = new AtomicLong();
        TestCache target = new TestCache();
        target.failReads = true;
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        Cache cache = manager(target, nanoTime, meterRegistry).getCache("subjectFilters");

        assertThat(cache).isNotNull();
        assertThat(cache.get("criteria", () -> "database-value")).isEqualTo("database-value");

        target.failReads = false;
        target.cachedValue = "fresh-redis-value";
        nanoTime.set(Duration.ofSeconds(6).toNanos());

        assertThat(cache.get("criteria", String.class)).isNull();
        assertThat(target.clears).hasValue(1);
        assertThat(meterRegistry.counter("subject.cache.redis.recoveries").count()).isEqualTo(1);
    }

    @Test
    void doesNotExecuteLoaderTwiceWhenRedisPutFailsAfterLoading() {
        AtomicLong nanoTime = new AtomicLong();
        TestCache target = new TestCache();
        target.failAfterLoading = true;
        Cache cache = manager(target, nanoTime, new SimpleMeterRegistry()).getCache("subjectFilters");
        AtomicInteger loads = new AtomicInteger();

        assertThat(cache).isNotNull();
        String value = cache.get("criteria", () -> {
            loads.incrementAndGet();
            return "database-value";
        });

        assertThat(value).isEqualTo("database-value");
        assertThat(loads).hasValue(1);
    }

    private ResilientRedisCacheManager manager(
            TestCache target,
            AtomicLong nanoTime,
            SimpleMeterRegistry meterRegistry) {
        CacheManager delegate = new CacheManager() {
            @Override
            public Cache getCache(String name) {
                return target;
            }

            @Override
            public Collection<String> getCacheNames() {
                return List.of(target.getName());
            }
        };
        return new ResilientRedisCacheManager(
                delegate,
                Duration.ofSeconds(5),
                meterRegistry,
                nanoTime::get);
    }

    private static final class TestCache implements Cache {

        private final AtomicInteger readAttempts = new AtomicInteger();
        private final AtomicInteger clears = new AtomicInteger();
        private boolean failReads;
        private boolean failAfterLoading;
        private Object cachedValue;

        @Override
        public String getName() {
            return "subjectFilters";
        }

        @Override
        public Object getNativeCache() {
            return this;
        }

        @Override
        public ValueWrapper get(Object key) {
            readAttempts.incrementAndGet();
            failIfNeeded();
            return cachedValue == null ? null : new SimpleValueWrapper(cachedValue);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(Object key, Class<T> type) {
            readAttempts.incrementAndGet();
            failIfNeeded();
            return (T) cachedValue;
        }

        @Override
        public <T> T get(Object key, Callable<T> valueLoader) {
            readAttempts.incrementAndGet();
            failIfNeeded();
            try {
                if (cachedValue != null) {
                    @SuppressWarnings("unchecked")
                    T value = (T) cachedValue;
                    return value;
                }
                T value = valueLoader.call();
                if (failAfterLoading) {
                    throw new IllegalStateException("redis put failed");
                }
                cachedValue = value;
                return value;
            } catch (Cache.ValueRetrievalException exception) {
                throw exception;
            } catch (Exception exception) {
                if (exception instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new Cache.ValueRetrievalException(key, valueLoader, exception);
            }
        }

        @Override
        public void put(Object key, Object value) {
            cachedValue = value;
        }

        @Override
        public void evict(Object key) {
            cachedValue = null;
        }

        @Override
        public void clear() {
            clears.incrementAndGet();
            cachedValue = null;
        }

        private void failIfNeeded() {
            if (failReads) {
                throw new IllegalStateException("redis unavailable");
            }
        }
    }
}
