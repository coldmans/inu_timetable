package inu.timetable.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/**
 * Keeps read APIs available when Redis is temporarily unreachable.
 *
 * <p>While degraded, cache reads become misses and writes are skipped. Before Redis is used
 * again, every subject cache is cleared once so values that became stale during the outage
 * cannot be served after recovery.</p>
 */
@Slf4j
public final class ResilientRedisCacheManager implements CacheManager {

    private final CacheManager delegate;
    private final MeterRegistry meterRegistry;
    private final long retryAfterNanos;
    private final LongSupplier nanoTime;
    private final Counter failureCounter;
    private final Counter recoveryCounter;
    private final Map<String, Cache> caches = new ConcurrentHashMap<>();
    private final AtomicBoolean degraded = new AtomicBoolean();
    private final AtomicBoolean recoveryInProgress = new AtomicBoolean();

    private volatile long retryAtNanos;

    public ResilientRedisCacheManager(
            CacheManager delegate,
            Duration retryAfter,
            MeterRegistry meterRegistry) {
        this(delegate, retryAfter, meterRegistry, System::nanoTime);
    }

    ResilientRedisCacheManager(
            CacheManager delegate,
            Duration retryAfter,
            MeterRegistry meterRegistry,
            LongSupplier nanoTime) {
        this.delegate = delegate;
        this.meterRegistry = meterRegistry;
        this.retryAfterNanos = Math.max(0, retryAfter.toNanos());
        this.nanoTime = nanoTime;
        this.failureCounter = Counter.builder("subject.cache.redis.failures")
                .description("Redis cache operations that failed and fell back to the database")
                .register(meterRegistry);
        this.recoveryCounter = Counter.builder("subject.cache.redis.recoveries")
                .description("Redis cache recoveries completed after flushing stale values")
                .register(meterRegistry);
    }

    @Override
    public Cache getCache(String name) {
        Cache target = delegate.getCache(name);
        if (target == null) {
            return null;
        }
        return caches.computeIfAbsent(name, ignored -> new ResilientRedisCache(
                target,
                Counter.builder("subject.cache.requests")
                        .tag("cache", name)
                        .tag("result", "hit")
                        .register(meterRegistry),
                Counter.builder("subject.cache.requests")
                        .tag("cache", name)
                        .tag("result", "miss")
                        .register(meterRegistry),
                Counter.builder("subject.cache.requests")
                        .tag("cache", name)
                        .tag("result", "bypass")
                        .register(meterRegistry)));
    }

    @Override
    public Collection<String> getCacheNames() {
        return delegate.getCacheNames();
    }

    private boolean redisAvailable() {
        if (!degraded.get()) {
            return true;
        }
        if (nanoTime.getAsLong() < retryAtNanos || !recoveryInProgress.compareAndSet(false, true)) {
            return false;
        }
        try {
            for (String cacheName : delegate.getCacheNames()) {
                Cache cache = delegate.getCache(cacheName);
                if (cache != null) {
                    cache.clear();
                }
            }
            degraded.set(false);
            retryAtNanos = 0;
            recoveryCounter.increment();
            log.info("Redis subject cache recovered after clearing stale values");
            return true;
        } catch (RuntimeException exception) {
            markFailure(exception);
            return false;
        } finally {
            recoveryInProgress.set(false);
        }
    }

    private void markFailure(RuntimeException exception) {
        failureCounter.increment();
        retryAtNanos = nanoTime.getAsLong() + retryAfterNanos;
        if (degraded.compareAndSet(false, true)) {
            log.warn(
                    "Redis subject cache unavailable; using database fallback for {} ms: {}",
                    Duration.ofNanos(retryAfterNanos).toMillis(),
                    exception.getMessage());
        }
    }

    private final class ResilientRedisCache implements Cache {

        private final Cache target;
        private final Counter hitCounter;
        private final Counter missCounter;
        private final Counter bypassCounter;

        private ResilientRedisCache(
                Cache target,
                Counter hitCounter,
                Counter missCounter,
                Counter bypassCounter) {
            this.target = target;
            this.hitCounter = hitCounter;
            this.missCounter = missCounter;
            this.bypassCounter = bypassCounter;
        }

        @Override
        public String getName() {
            return target.getName();
        }

        @Override
        public Object getNativeCache() {
            return target.getNativeCache();
        }

        @Override
        public ValueWrapper get(Object key) {
            if (!redisAvailable()) {
                bypassCounter.increment();
                return null;
            }
            try {
                ValueWrapper value = target.get(key);
                counterFor(value != null).increment();
                return value;
            } catch (RuntimeException exception) {
                markFailure(exception);
                bypassCounter.increment();
                return null;
            }
        }

        @Override
        public <T> T get(Object key, Class<T> type) {
            if (!redisAvailable()) {
                bypassCounter.increment();
                return null;
            }
            try {
                T value = target.get(key, type);
                counterFor(value != null).increment();
                return value;
            } catch (RuntimeException exception) {
                markFailure(exception);
                bypassCounter.increment();
                return null;
            }
        }

        @Override
        public <T> T get(Object key, Callable<T> valueLoader) {
            if (!redisAvailable()) {
                bypassCounter.increment();
                return callLoader(key, valueLoader);
            }

            AtomicBoolean loaded = new AtomicBoolean();
            AtomicReference<T> loadedValue = new AtomicReference<>();
            try {
                T value = target.get(key, () -> {
                    T result = valueLoader.call();
                    loadedValue.set(result);
                    loaded.set(true);
                    return result;
                });
                counterFor(!loaded.get()).increment();
                return value;
            } catch (ValueRetrievalException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                markFailure(exception);
                bypassCounter.increment();
                if (loaded.get()) {
                    return loadedValue.get();
                }
                return callLoader(key, valueLoader);
            }
        }

        @Override
        public void put(Object key, Object value) {
            if (!redisAvailable()) {
                return;
            }
            try {
                target.put(key, value);
            } catch (RuntimeException exception) {
                markFailure(exception);
            }
        }

        @Override
        public ValueWrapper putIfAbsent(Object key, Object value) {
            if (!redisAvailable()) {
                return null;
            }
            try {
                return target.putIfAbsent(key, value);
            } catch (RuntimeException exception) {
                markFailure(exception);
                return null;
            }
        }

        @Override
        public void evict(Object key) {
            if (!redisAvailable()) {
                return;
            }
            try {
                target.evict(key);
            } catch (RuntimeException exception) {
                markFailure(exception);
            }
        }

        @Override
        public boolean evictIfPresent(Object key) {
            if (!redisAvailable()) {
                return false;
            }
            try {
                return target.evictIfPresent(key);
            } catch (RuntimeException exception) {
                markFailure(exception);
                return false;
            }
        }

        @Override
        public void clear() {
            if (!redisAvailable()) {
                return;
            }
            try {
                target.clear();
            } catch (RuntimeException exception) {
                markFailure(exception);
            }
        }

        @Override
        public boolean invalidate() {
            if (!redisAvailable()) {
                return false;
            }
            try {
                return target.invalidate();
            } catch (RuntimeException exception) {
                markFailure(exception);
                return false;
            }
        }

        private Counter counterFor(boolean hit) {
            return hit ? hitCounter : missCounter;
        }

        private <T> T callLoader(Object key, Callable<T> valueLoader) {
            try {
                return valueLoader.call();
            } catch (Exception exception) {
                throw new ValueRetrievalException(key, valueLoader, exception);
            }
        }
    }
}
