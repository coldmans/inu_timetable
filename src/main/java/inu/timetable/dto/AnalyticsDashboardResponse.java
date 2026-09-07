package inu.timetable.dto;

import java.util.List;

/**
 * 관리자 운영 대시보드용 사용자 중심 분석 응답.
 */
public record AnalyticsDashboardResponse(
        RangeInfo range,
        MetricSnapshot current,
        MetricSnapshot previous,
        AccountStatus accounts,
        List<TrendPoint> trend,
        List<ActionBreakdown> actions,
        List<SearchTerm> topSearches
) {
    public record RangeInfo(
            String key,
            String label,
            String timezone,
            String from,
            String to,
            long elapsedMinutes,
            String elapsedLabel,
            String comparisonLabel,
            String bucket
    ) {
    }

    public record MetricSnapshot(
            long totalEvents,
            long trackedActors,
            long coreActionActors,
            long accountActors,
            long anonymousActors,
            long searchActors,
            long searchAndCoreActors,
            long timetableIntentActors,
            long wishlistIntentActors,
            long combinationActors,
            long persistedActionUsers,
            long timetableUsers,
            long timetableRows,
            long wishlistUsers,
            long wishlistRows,
            long bothPersistedUsers,
            long newUsers
    ) {
    }

    public record AccountStatus(
            long dau,
            long mau,
            long totalAccounts,
            long activeAccounts
    ) {
    }

    public record TrendPoint(
            String bucket,
            String label,
            long events,
            long actors
    ) {
    }

    public record ActionBreakdown(
            String type,
            long events,
            long actors,
            double eventsPerActor
    ) {
    }

    public record SearchTerm(
            String term,
            long events,
            long actors
    ) {
    }
}
