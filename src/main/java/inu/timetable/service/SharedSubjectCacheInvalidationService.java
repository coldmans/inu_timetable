package inu.timetable.service;

import inu.timetable.event.SubjectDataChangedEvent;
import inu.timetable.event.SubjectPopularityChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class SharedSubjectCacheInvalidationService {

    static final String SCOPE_ALL = "subject-all";
    static final String SCOPE_FILTERS = "subject-filters";

    private final JdbcTemplate jdbcTemplate;
    private final CacheManager cacheManager;
    private final Map<String, Long> observedVersions = new ConcurrentHashMap<>();

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void publishSubjectDataChanged(SubjectDataChangedEvent event) {
        incrementVersion(SCOPE_ALL);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void publishPopularityChanged(SubjectPopularityChangedEvent event) {
        incrementVersion(SCOPE_FILTERS);
    }

    @Scheduled(fixedDelayString = "${subject.cache.invalidation.poll-interval-ms:1000}")
    public void synchronizeLocalCaches() {
        jdbcTemplate.query("""
                        SELECT cache_scope, version
                        FROM shared_cache_versions
                        """,
                resultSet -> {
                    String scope = resultSet.getString("cache_scope");
                    long currentVersion = resultSet.getLong("version");
                    Long previousVersion = observedVersions.put(scope, currentVersion);
                    if (previousVersion != null && currentVersion > previousVersion) {
                        clearScope(scope);
                        log.info(
                                "Applied shared cache invalidation: scope={}, version={}",
                                scope,
                                currentVersion);
                    }
                });
    }

    private void incrementVersion(String scope) {
        jdbcTemplate.update("""
                        UPDATE shared_cache_versions
                        SET version = version + 1,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE cache_scope = ?
                        """,
                scope);
    }

    private void clearScope(String scope) {
        if (SCOPE_ALL.equals(scope)) {
            SubjectCacheNames.ALL.forEach(this::clearCache);
        } else if (SCOPE_FILTERS.equals(scope)) {
            clearCache(SubjectCacheNames.SUBJECT_FILTERS);
        }
    }

    private void clearCache(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }
}
