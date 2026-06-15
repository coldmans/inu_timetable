package inu.timetable.service;

import inu.timetable.entity.User;
import inu.timetable.entity.UserActivityDaily;
import inu.timetable.enums.UserStatus;
import inu.timetable.repository.UserActivityDailyRepository;
import inu.timetable.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserActivityServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-14T08:30:00Z"),
            ZoneId.of("Asia/Seoul"));

    private UserActivityDailyRepository userActivityDailyRepository;
    private UserRepository userRepository;
    private EntityManager entityManager;
    private UserActivityService userActivityService;

    @BeforeEach
    void setUp() {
        userActivityDailyRepository = mock(UserActivityDailyRepository.class);
        userRepository = mock(UserRepository.class);
        entityManager = mock(EntityManager.class);
        userActivityService = new UserActivityService(userActivityDailyRepository, userRepository, entityManager, FIXED_CLOCK);
    }

    @Test
    void recordActivityStoresOneDailyRowPerUser() {
        User user = User.builder().id(42L).username("student").password("pw").build();
        when(userRepository.existsByIdAndStatus(42L, UserStatus.ACTIVE)).thenReturn(true);
        when(userActivityDailyRepository.existsByUserIdAndActivityDate(42L, LocalDate.of(2026, 6, 14)))
                .thenReturn(false);
        when(entityManager.getReference(User.class, 42L)).thenReturn(user);

        userActivityService.recordActivity(42L);
        userActivityService.recordActivity(42L);

        ArgumentCaptor<UserActivityDaily> activityCaptor = ArgumentCaptor.forClass(UserActivityDaily.class);
        verify(userActivityDailyRepository).save(activityCaptor.capture());
        UserActivityDaily savedActivity = activityCaptor.getValue();
        assertThat(savedActivity.getUser()).isSameAs(user);
        assertThat(savedActivity.getActivityDate()).isEqualTo(LocalDate.of(2026, 6, 14));
        assertThat(savedActivity.getFirstSeenAt()).isEqualTo(LocalDateTime.of(2026, 6, 14, 17, 30));
    }

    @Test
    void recordActivitySkipsDatabaseWhenDailyRowAlreadyExists() {
        when(userRepository.existsByIdAndStatus(42L, UserStatus.ACTIVE)).thenReturn(true);
        when(userActivityDailyRepository.existsByUserIdAndActivityDate(42L, LocalDate.of(2026, 6, 14)))
                .thenReturn(true);

        userActivityService.recordActivity(42L);

        verify(entityManager, never()).getReference(User.class, 42L);
        verify(userActivityDailyRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void recordActivitySkipsUnknownOrInactiveUser() {
        when(userRepository.existsByIdAndStatus(42L, UserStatus.ACTIVE)).thenReturn(false);

        userActivityService.recordActivity(42L);

        verify(userActivityDailyRepository, never())
                .existsByUserIdAndActivityDate(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
        verify(entityManager, never()).getReference(User.class, 42L);
        verify(userActivityDailyRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void countMauUsesRecentThirtyDayWindow() {
        when(userActivityDailyRepository.countDistinctUsersBetween(
                LocalDate.of(2026, 5, 16),
                LocalDate.of(2026, 6, 14)))
                .thenReturn(2500L);

        long mau = userActivityService.countMau();

        assertThat(mau).isEqualTo(2500L);
    }
}
