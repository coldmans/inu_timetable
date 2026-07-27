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
                new SharedSubjectCacheInvalidationService(jdbcTemplate, firstManager);
        SharedSubjectCacheInvalidationService secondInstance =
                new SharedSubjectCacheInvalidationService(jdbcTemplate, secondManager);

        firstInstance.synchronizeLocalCaches();
        secondInstance.synchronizeLocalCaches();
        Cache secondCache = secondManager.getCache(SubjectCacheNames.SUBJECT_NAME_SEARCH);
        assertThat(secondCache).isNotNull();
        secondCache.put("criteria", "cached-value");

        firstInstance.publishSubjectDataChanged(new SubjectDataChangedEvent("test"));
        secondInstance.synchronizeLocalCaches();

        assertThat(secondCache.get("criteria")).isNull();
    }

    private CaffeineCacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCacheNames(SubjectCacheNames.ALL);
        return manager;
    }
}
