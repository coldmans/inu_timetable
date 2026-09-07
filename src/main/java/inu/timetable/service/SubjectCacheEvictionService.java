package inu.timetable.service;

import inu.timetable.event.SubjectDataChangedEvent;
import inu.timetable.event.SubjectPopularityChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@RequiredArgsConstructor
public class SubjectCacheEvictionService {

    private final CacheManager cacheManager;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void evictAfterSubjectDataChanged(SubjectDataChangedEvent event) {
        evictAllSubjectReadCaches();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void evictFilterAfterPopularityChanged(SubjectPopularityChangedEvent event) {
        clearCache(SubjectCacheNames.SUBJECT_FILTERS);
    }

    public void evictAllSubjectReadCaches() {
        for (String cacheName : SubjectCacheNames.ALL) {
            clearCache(cacheName);
        }
    }

    private void clearCache(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }
}
