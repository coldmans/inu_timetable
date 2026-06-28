package inu.timetable.service;

import inu.timetable.entity.User;
import inu.timetable.entity.UserActivityDaily;
import inu.timetable.enums.UserStatus;
import inu.timetable.repository.UserActivityDailyRepository;
import inu.timetable.repository.UserRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class UserActivityService {

    private final UserActivityDailyRepository userActivityDailyRepository;
    private final UserRepository userRepository;
    private final EntityManager entityManager;
    private final Clock clock;
    private final Set<String> recordedToday = ConcurrentHashMap.newKeySet();
    private volatile LocalDate cacheDate;

    @Transactional
    public void recordActivity(Long userId) {
        if (userId == null) {
            return;
        }

        LocalDate today = LocalDate.now(clock);
        resetCacheIfDateChanged(today);

        String cacheKey = userId + ":" + today;
        if (recordedToday.contains(cacheKey)) {
            return;
        }

        if (!userRepository.existsByIdAndStatus(userId, UserStatus.ACTIVE)) {
            return;
        }

        if (!recordedToday.add(cacheKey)) {
            return;
        }

        try {
            if (userActivityDailyRepository.existsByUserIdAndActivityDate(userId, today)) {
                return;
            }

            User userReference = entityManager.getReference(User.class, userId);
            userActivityDailyRepository.save(UserActivityDaily.builder()
                    .user(userReference)
                    .activityDate(today)
                    .firstSeenAt(LocalDateTime.now(clock))
                    .build());
        } catch (DataIntegrityViolationException alreadyRecorded) {
            // 동시 요청으로 같은 날짜 행이 이미 저장됨 — 멱등 처리(정상). 캐시는 유지한다.
        } catch (RuntimeException ex) {
            // 그 외 저장 실패 시 캐시를 되돌려, 같은 사용자의 다음 요청이 재기록을 시도하게 한다.
            recordedToday.remove(cacheKey);
            throw ex;
        }
    }

    public long countDau() {
        LocalDate today = LocalDate.now(clock);
        return userActivityDailyRepository.countDistinctUsersBetween(today, today);
    }

    public long countMau() {
        LocalDate today = LocalDate.now(clock);
        return userActivityDailyRepository.countDistinctUsersBetween(today.minusDays(29), today);
    }

    private void resetCacheIfDateChanged(LocalDate today) {
        if (today.equals(cacheDate)) {
            return;
        }

        synchronized (this) {
            if (!today.equals(cacheDate)) {
                recordedToday.clear();
                cacheDate = today;
            }
        }
    }
}
