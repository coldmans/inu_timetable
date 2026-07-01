package inu.timetable.dto;

import java.util.List;

/**
 * 관리자 대시보드용 분석 요약.
 */
public record AnalyticsSummaryResponse(
        long dau,
        long mau,
        long totalEvents,
        int rangeDays,
        List<TypeCount> byType,
        List<DailyPoint> daily,
        List<SearchTerm> topSearches
) {
    public record TypeCount(String type, long count) {}
    public record DailyPoint(String date, long count) {}
    public record SearchTerm(String term, long count) {}
}
