package inu.timetable.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Combines a per-instance Caffeine L1 with a shared, resilient Redis L2.
 *
 * <p>Hot reads remain in-process. New Cloud Run instances can reuse values already loaded by
 * another instance, while a Redis outage still falls through to PostgreSQL and keeps L1 useful.</p>
 */
public final class TwoLevelCacheManager implements CacheManager {

    private final CacheManager local;
    private final CacheManager shared;
    private final MeterRegistry meterRegistry;
    private final Map<String, Cache> caches = new ConcurrentHashMap<>();

    public TwoLevelCacheManager(
            CacheManager local,
            CacheManager shared,
            MeterRegistry meterRegistry) {
        this.local = local;
        this.shared = shared;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Cache getCache(String name) {
        Cache localCache = local.getCache(name);
        Cache sharedCache = shared.getCache(name);
        if (localCache == null || sharedCache == null) {
            return null;
        }
        return caches.computeIfAbsent(name, ignored -> new TwoLevelCache(
                localCache,
                sharedCache,
                Counter.builder("subject.cache.l1.requests")
                        .tag("cache", name)
                        .tag("result", "hit")
                        .register(meterRegistry),
                Counter.builder("subject.cache.l1.requests")
                        .tag("cache", name)
                        .tag("result", "miss")
                        .register(meterRegistry)));
    }

    @Override
    public Collection<String> getCacheNames() {
        return local.getCacheNames();
    }

    private static final class TwoLevelCache implements Cache {

        private final Cache local;
        private final Cache shared;
        private final Counter localHitCounter;
        private final Counter localMissCounter;

        private TwoLevelCache(
                Cache local,
                Cache shared,
                Counter localHitCounter,
                Counter localMissCounter) {
            this.local = local;
            this.shared = shared;
            this.localHitCounter = localHitCounter;
            this.localMissCounter = localMissCounter;
        }

        @Override
        public String getName() {
            return local.getName();
        }

        @Override
        public Object getNativeCache() {
            return local.getNativeCache();
        }

        @Override
        public ValueWrapper get(Object key) {
            ValueWrapper localValue = local.get(key);
            if (localValue != null) {
                localHitCounter.increment();
                return localValue;
            }
            localMissCounter.increment();
            ValueWrapper sharedValue = shared.get(key);
            if (sharedValue != null) {
                local.put(key, sharedValue.get());
            }
            return sharedValue;
        }

        @Override
        public <T> T get(Object key, Class<T> type) {
            T localValue = local.get(key, type);
            if (localValue != null) {
                localHitCounter.increment();
                return localValue;
            }
            localMissCounter.increment();
            T sharedValue = shared.get(key, type);
            if (sharedValue != null) {
                local.put(key, sharedValue);
            }
            return sharedValue;
        }

        @Override
        public <T> T get(Object key, Callable<T> valueLoader) {
            AtomicBoolean localMiss = new AtomicBoolean();
            T value = local.get(key, () -> {
                localMiss.set(true);
                return shared.get(key, valueLoader);
            });
            if (localMiss.get()) {
                localMissCounter.increment();
            } else {
                localHitCounter.increment();
            }
            return value;
        }

        @Override
        public void put(Object key, Object value) {
            shared.put(key, value);
            local.put(key, value);
        }

        @Override
        public ValueWrapper putIfAbsent(Object key, Object value) {
            ValueWrapper existing = get(key);
            if (existing != null) {
                return existing;
            }
            put(key, value);
            return null;
        }

        @Override
        public void evict(Object key) {
            shared.evict(key);
            local.evict(key);
        }

        @Override
        public boolean evictIfPresent(Object key) {
            boolean sharedEvicted = shared.evictIfPresent(key);
            boolean localEvicted = local.evictIfPresent(key);
            return sharedEvicted || localEvicted;
        }

        @Override
        public void clear() {
            shared.clear();
            local.clear();
        }

        @Override
        public boolean invalidate() {
            boolean sharedInvalidated = shared.invalidate();
            boolean localInvalidated = local.invalidate();
            return sharedInvalidated || localInvalidated;
        }
    }
}
