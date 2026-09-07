package inu.timetable.service;

import inu.timetable.dto.AnalyticsDashboardResponse;
import inu.timetable.repository.AdminAnalyticsRepository;
import inu.timetable.repository.AdminAnalyticsRepository.AccountTotals;
import inu.timetable.repository.AdminAnalyticsRepository.ActionRow;
import inu.timetable.repository.AdminAnalyticsRepository.SearchRow;
import inu.timetable.repository.AdminAnalyticsRepository.Snapshot;
import inu.timetable.repository.AdminAnalyticsRepository.TrendBucket;
import inu.timetable.repository.AdminAnalyticsRepository.TrendRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalyticsDashboardServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-28T16:02:41Z"),
            ZoneOffset.UTC);

    private AdminAnalyticsRepository repository;
    private UserActivityService userActivityService;
    private AnalyticsDashboardService service;

    @BeforeEach
    void setUp() {
        repository = mock(AdminAnalyticsRepository.class);
        userActivityService = mock(UserActivityService.class);
        service = new AnalyticsDashboardService(repository, userActivityService, CLOCK);
    }

    @Test
    void todayUsesKoreaMidnightAndShowsExactElapsedTime() {
        LocalDateTime from = LocalDateTime.of(2026, 7, 28, 15, 0);
        LocalDateTime to = LocalDateTime.of(2026, 7, 28, 16, 2, 41);
        LocalDateTime previousFrom = LocalDateTime.of(2026, 7, 27, 15, 0);
        LocalDateTime previousTo = LocalDateTime.of(2026, 7, 27, 16, 2, 41);

        Snapshot current = snapshot(827, 48, 44, 36, 17);
        Snapshot previous = snapshot(600, 40, 35, 29, 11);
        when(repository.loadSnapshot(from, to)).thenReturn(current);
        when(repository.loadSnapshot(previousFrom, previousTo)).thenReturn(previous);
        when(repository.loadAccountTotals()).thenReturn(new AccountTotals(3224, 3216));
        when(userActivityService.countDau()).thenReturn(48L);
        when(userActivityService.countMau()).thenReturn(724L);
        when(repository.loadTrend(from, to, TrendBucket.HOUR))
                .thenReturn(List.of(new TrendRow(
                        LocalDateTime.of(2026, 7, 29, 1, 0),
                        827,
                        48)));
        when(repository.loadActionBreakdown(from, to))
                .thenReturn(List.of(new ActionRow("TIMETABLE_ADD", 587, 43)));
        when(repository.loadTopSearches(from, to, 10))
                .thenReturn(List.of(new SearchRow("대학수학", 12, 8)));

        AnalyticsDashboardResponse response = service.dashboard("today");

        assertThat(response.range().key()).isEqualTo("today");
        assertThat(response.range().timezone()).isEqualTo("Asia/Seoul");
        assertThat(response.range().from()).isEqualTo("2026-07-29T00:00:00+09:00");
        assertThat(response.range().to()).isEqualTo("2026-07-29T01:02:41+09:00");
        assertThat(response.range().elapsedMinutes()).isEqualTo(62);
        assertThat(response.range().elapsedLabel()).isEqualTo("1시간 2분");
        assertThat(response.range().comparisonLabel()).isEqualTo("전날 같은 시간대");
        assertThat(response.current().trackedActors()).isEqualTo(48);
        assertThat(response.current().persistedActionUsers()).isEqualTo(36);
        assertThat(response.current().newUsers()).isEqualTo(17);
        assertThat(response.accounts().dau()).isEqualTo(48);
        assertThat(response.trend()).hasSize(2);
        assertThat(response.trend().get(0).events()).isZero();
        assertThat(response.trend().get(1).events()).isEqualTo(827);

        verify(repository).loadSnapshot(from, to);
        verify(repository).loadSnapshot(previousFrom, previousTo);
    }

    @Test
    void rollingSixHoursUsesPreviousAdjacentSixHours() {
        LocalDateTime from = LocalDateTime.of(2026, 7, 28, 10, 2, 41);
        LocalDateTime to = LocalDateTime.of(2026, 7, 28, 16, 2, 41);
        LocalDateTime previousFrom = LocalDateTime.of(2026, 7, 28, 4, 2, 41);
        LocalDateTime previousTo = from;

        when(repository.loadSnapshot(from, to)).thenReturn(snapshot(10, 4, 3, 2, 1));
        when(repository.loadSnapshot(previousFrom, previousTo)).thenReturn(snapshot(8, 3, 2, 1, 0));
        when(repository.loadAccountTotals()).thenReturn(new AccountTotals(3224, 3216));
        when(repository.loadTrend(from, to, TrendBucket.HOUR)).thenReturn(List.of());
        when(repository.loadActionBreakdown(from, to)).thenReturn(List.of());
        when(repository.loadTopSearches(from, to, 10)).thenReturn(List.of());

        AnalyticsDashboardResponse response = service.dashboard("6h");

        assertThat(response.range().elapsedMinutes()).isEqualTo(360);
        assertThat(response.range().comparisonLabel()).isEqualTo("직전 6시간");
        verify(repository).loadSnapshot(from, to);
        verify(repository).loadSnapshot(previousFrom, previousTo);
    }

    private Snapshot snapshot(
            long totalEvents,
            long trackedActors,
            long coreActors,
            long persistedUsers,
            long newUsers) {
        return new Snapshot(
                totalEvents,
                trackedActors,
                coreActors,
                37,
                7,
                21,
                18,
                43,
                10,
                5,
                persistedUsers,
                35,
                143,
                9,
                30,
                8,
                newUsers);
    }
}
