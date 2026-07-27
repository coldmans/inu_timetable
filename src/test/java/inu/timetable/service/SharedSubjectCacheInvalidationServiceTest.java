package inu.timetable.service;

import inu.timetable.event.SubjectDataChangedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
class SharedSubjectCacheInvalidationServiceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void databaseVersionInvalidatesAnotherInstancesLocalCache() {
        CaffeineCacheManager firstManager = cacheManager();
        CaffeineCacheManager secondManager = cacheManager();
        SharedSubjectCacheInvalidationService firstInstance =
                new SharedSubjectCacheInvalidationService(jdbcTemplate, firstManager, true, true);
        SharedSubjectCacheInvalidationService secondInstance =
                new SharedSubjectCacheInvalidationService(jdbcTemplate, secondManager, true, true);

        firstInstance.synchronizeLocalCaches();
        secondInstance.synchronizeLocalCaches();
        Cache secondCache = secondManager.getCache(SubjectCacheNames.SUBJECT_NAME_SEARCH);
        assertThat(secondCache).isNotNull();
        secondCache.put("criteria", "cached-value");

        firstInstance.publishSubjectDataChanged(new SubjectDataChangedEvent("test"));
        secondInstance.synchronizeLocalCaches();

        assertThat(secondCache.get("criteria")).isNull();
    }

    @Test
    void disabledCompatibilityBridgeDoesNotPollOrPublishDatabaseVersions() {
        CaffeineCacheManager manager = cacheManager();
        SharedSubjectCacheInvalidationService service =
                new SharedSubjectCacheInvalidationService(jdbcTemplate, manager, false, false);

        Long initialVersion = versionOf(SharedSubjectCacheInvalidationService.SCOPE_ALL);
        Cache cache = manager.getCache(SubjectCacheNames.SUBJECT_NAME_SEARCH);
        assertThat(cache).isNotNull();
        cache.put("criteria", "cached-value");

        service.publishSubjectDataChanged(new SubjectDataChangedEvent("test"));
        jdbcTemplate.update(
                "UPDATE shared_cache_versions SET version = version + 1 WHERE cache_scope = ?",
                SharedSubjectCacheInvalidationService.SCOPE_ALL);
        service.synchronizeLocalCaches();

        assertThat(versionOf(SharedSubjectCacheInvalidationService.SCOPE_ALL))
                .isEqualTo(initialVersion + 1);
        assertThat(cache.get("criteria", String.class)).isEqualTo("cached-value");
    }

    private Long versionOf(String scope) {
        return jdbcTemplate.queryForObject(
                "SELECT version FROM shared_cache_versions WHERE cache_scope = ?",
                Long.class,
                scope);
    }

    private CaffeineCacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCacheNames(SubjectCacheNames.ALL);
        return manager;
    }
}
