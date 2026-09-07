package inu.timetable.service;

import inu.timetable.dto.AnalyticsDashboardResponse;
import inu.timetable.dto.AnalyticsDashboardResponse.AccountStatus;
import inu.timetable.dto.AnalyticsDashboardResponse.ActionBreakdown;
import inu.timetable.dto.AnalyticsDashboardResponse.MetricSnapshot;
import inu.timetable.dto.AnalyticsDashboardResponse.RangeInfo;
import inu.timetable.dto.AnalyticsDashboardResponse.SearchTerm;
import inu.timetable.dto.AnalyticsDashboardResponse.TrendPoint;
import inu.timetable.repository.AdminAnalyticsRepository;
import inu.timetable.repository.AdminAnalyticsRepository.AccountTotals;
import inu.timetable.repository.AdminAnalyticsRepository.Snapshot;
import inu.timetable.repository.AdminAnalyticsRepository.TrendBucket;
import inu.timetable.repository.AdminAnalyticsRepository.TrendRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AnalyticsDashboardService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter OFFSET_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final DateTimeFormatter HOUR_LABEL = DateTimeFormatter.ofPattern("M/d HH시");
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("M/d");

    private final AdminAnalyticsRepository repository;
    private final UserActivityService userActivityService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AnalyticsDashboardResponse dashboard(String rawRange) {
        RangePreset preset = RangePreset.parse(rawRange);
        ResolvedRange range = preset.resolve(clock.instant().atZone(BUSINESS_ZONE));
        LocalDateTime fromUtc = toUtcLocalDateTime(range.from());
        LocalDateTime toUtc = toUtcLocalDateTime(range.to());
        LocalDateTime previousFromUtc = toUtcLocalDateTime(range.previousFrom());
        LocalDateTime previousToUtc = toUtcLocalDateTime(range.previousTo());

        Snapshot current = repository.loadSnapshot(fromUtc, toUtc);
        Snapshot previous = repository.loadSnapshot(previousFromUtc, previousToUtc);
        AccountTotals accountTotals = repository.loadAccountTotals();
        TrendBucket bucket = range.elapsed().compareTo(Duration.ofHours(48)) <= 0
                ? TrendBucket.HOUR
                : TrendBucket.DAY;

        List<TrendPoint> trend = fillTrend(
                range,
                bucket,
                repository.loadTrend(fromUtc, toUtc, bucket));
        List<ActionBreakdown> actions = repository.loadActionBreakdown(fromUtc, toUtc).stream()
                .map(row -> new ActionBreakdown(
                        row.eventType(),
                        row.eventCount(),
                        row.actorCount(),
                        perActor(row.eventCount(), row.actorCount())))
                .toList();
        List<SearchTerm> topSearches = repository.loadTopSearches(fromUtc, toUtc, 10).stream()
                .map(row -> new SearchTerm(row.term(), row.eventCount(), row.actorCount()))
                .toList();

        return new AnalyticsDashboardResponse(
                new RangeInfo(
                        preset.key,
                        preset.label,
                        BUSINESS_ZONE.getId(),
                        formatOffset(range.from()),
                        formatOffset(range.to()),
                        range.elapsed().toMinutes(),
                        elapsedLabel(range.elapsed()),
                        preset.comparisonLabel,
                        bucket.name().toLowerCase(Locale.ROOT)),
                toMetricSnapshot(current),
                toMetricSnapshot(previous),
                new AccountStatus(
                        userActivityService.countDau(),
                        userActivityService.countMau(),
                        accountTotals.totalAccounts(),
                        accountTotals.activeAccounts()),
                trend,
                actions,
                topSearches);
    }

    private List<TrendPoint> fillTrend(
            ResolvedRange range,
            TrendBucket bucket,
            List<TrendRow> rows) {
        Map<LocalDateTime, TrendRow> rowsByBucket = new HashMap<>();
        rows.forEach(row -> rowsByBucket.put(row.bucketKst(), row));

        LocalDateTime cursor = truncate(range.from().toLocalDateTime(), bucket);
        LocalDateTime end = truncate(range.to().toLocalDateTime(), bucket);
        List<TrendPoint> result = new ArrayList<>();
        while (!cursor.isAfter(end)) {
            TrendRow row = rowsByBucket.get(cursor);
            result.add(new TrendPoint(
                    cursor.toString(),
                    cursor.format(bucket == TrendBucket.HOUR ? HOUR_LABEL : DAY_LABEL),
                    row == null ? 0 : row.eventCount(),
                    row == null ? 0 : row.actorCount()));
            cursor = bucket == TrendBucket.HOUR
                    ? cursor.plusHours(1)
                    : cursor.plusDays(1);
        }
        return result;
    }

    private LocalDateTime truncate(LocalDateTime value, TrendBucket bucket) {
        return bucket == TrendBucket.HOUR
                ? value.truncatedTo(ChronoUnit.HOURS)
                : value.toLocalDate().atStartOfDay();
    }

    private MetricSnapshot toMetricSnapshot(Snapshot snapshot) {
        return new MetricSnapshot(
                snapshot.totalEvents(),
                snapshot.trackedActors(),
                snapshot.coreActionActors(),
                snapshot.accountActors(),
                snapshot.anonymousActors(),
                snapshot.searchActors(),
                snapshot.searchAndCoreActors(),
                snapshot.timetableIntentActors(),
                snapshot.wishlistIntentActors(),
                snapshot.combinationActors(),
                snapshot.persistedActionUsers(),
                snapshot.timetableUsers(),
                snapshot.timetableRows(),
                snapshot.wishlistUsers(),
                snapshot.wishlistRows(),
                snapshot.bothPersistedUsers(),
                snapshot.newUsers());
    }

    private double perActor(long events, long actors) {
        if (actors == 0) {
            return 0;
        }
        return Math.round(events * 10.0 / actors) / 10.0;
    }

    private LocalDateTime toUtcLocalDateTime(ZonedDateTime value) {
        return LocalDateTime.ofInstant(value.toInstant(), ZoneOffset.UTC);
    }

    private String formatOffset(ZonedDateTime value) {
        return value.withNano(0).format(OFFSET_FORMATTER);
    }

    private String elapsedLabel(Duration duration) {
        long totalMinutes = duration.toMinutes();
        long days = totalMinutes / (24 * 60);
        long hours = (totalMinutes % (24 * 60)) / 60;
        long minutes = totalMinutes % 60;
        List<String> parts = new ArrayList<>();
        if (days > 0) {
            parts.add(days + "일");
        }
        if (hours > 0) {
            parts.add(hours + "시간");
        }
        if (minutes > 0 || parts.isEmpty()) {
            parts.add(minutes + "분");
        }
        return String.join(" ", parts);
    }

    private enum RangePreset {
        TODAY("today", "오늘", "전날 같은 시간대"),
        SIX_HOURS("6h", "6시간", "직전 6시간"),
        TWELVE_HOURS("12h", "12시간", "직전 12시간"),
        TWENTY_FOUR_HOURS("24h", "24시간", "직전 24시간"),
        SEVEN_DAYS("7d", "7일", "이전 7일 같은 구간"),
        THIRTY_DAYS("30d", "30일", "이전 30일 같은 구간");

        private final String key;
        private final String label;
        private final String comparisonLabel;

        RangePreset(String key, String label, String comparisonLabel) {
            this.key = key;
            this.label = label;
            this.comparisonLabel = comparisonLabel;
        }

        private static RangePreset parse(String raw) {
            if (raw == null) {
                return TODAY;
            }
            for (RangePreset value : values()) {
                if (value.key.equalsIgnoreCase(raw.trim())) {
                    return value;
                }
            }
            return TODAY;
        }

        private ResolvedRange resolve(ZonedDateTime now) {
            return switch (this) {
                case TODAY -> calendarRange(now, 0, 1);
                case SIX_HOURS -> rollingRange(now, Duration.ofHours(6));
                case TWELVE_HOURS -> rollingRange(now, Duration.ofHours(12));
                case TWENTY_FOUR_HOURS -> rollingRange(now, Duration.ofHours(24));
                case SEVEN_DAYS -> calendarRange(now, 6, 7);
                case THIRTY_DAYS -> calendarRange(now, 29, 30);
            };
        }

        private ResolvedRange rollingRange(ZonedDateTime now, Duration duration) {
            ZonedDateTime from = now.minus(duration);
            return new ResolvedRange(
                    from,
                    now,
                    from.minus(duration),
                    from);
        }

        private ResolvedRange calendarRange(
                ZonedDateTime now,
                int startDaysAgo,
                int comparisonShiftDays) {
            ZonedDateTime from = now.toLocalDate()
                    .minusDays(startDaysAgo)
                    .atStartOfDay(BUSINESS_ZONE);
            return new ResolvedRange(
                    from,
                    now,
                    from.minusDays(comparisonShiftDays),
                    now.minusDays(comparisonShiftDays));
        }
    }

    private record ResolvedRange(
            ZonedDateTime from,
            ZonedDateTime to,
            ZonedDateTime previousFrom,
            ZonedDateTime previousTo
    ) {
        private Duration elapsed() {
            return Duration.between(from, to);
        }
    }
}
