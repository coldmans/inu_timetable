package inu.timetable.service;

import inu.timetable.dto.AnalyticsSummaryResponse;
import inu.timetable.dto.AnalyticsSummaryResponse.DailyPoint;
import inu.timetable.dto.AnalyticsSummaryResponse.SearchTerm;
import inu.timetable.dto.AnalyticsSummaryResponse.TypeCount;
import inu.timetable.entity.AnalyticsEvent;
import inu.timetable.enums.AnalyticsEventType;
import inu.timetable.repository.AnalyticsEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final AnalyticsEventRepository analyticsEventRepository;
    private final UserActivityService userActivityService;
    private final Clock clock;

    @Transactional
    public void record(AnalyticsEventType type, Long userId, String label, String sessionId) {
        if (type == null) {
            return;
        }
        analyticsEventRepository.save(AnalyticsEvent.builder()
                .eventType(type)
                .userId(userId)
                .label(truncate(label, 255))
                .sessionId(truncate(sessionId, 64))
                .occurredAt(LocalDateTime.now(clock))
                .build());
    }

    @Transactional(readOnly = true)
    public AnalyticsSummaryResponse summary(int days) {
        LocalDate today = LocalDate.now(clock);
        LocalDateTime from = today.minusDays(days - 1L).atStartOfDay();

        long totalEvents = analyticsEventRepository.countByOccurredAtGreaterThanEqual(from);

        List<TypeCount> byType = analyticsEventRepository.countByType(from).stream()
                .map(row -> new TypeCount(row.getEventType().name(), row.getCount()))
                .toList();

        List<DailyPoint> daily = analyticsEventRepository.countDaily(from).stream()
                .map(row -> new DailyPoint(row.getDay().toString(), row.getCount()))
                .toList();

        List<SearchTerm> topSearches = analyticsEventRepository
                .topLabels(AnalyticsEventType.SEARCH, from, PageRequest.of(0, 10)).stream()
                .map(row -> new SearchTerm(row.getLabel(), row.getCount()))
                .toList();

        return new AnalyticsSummaryResponse(
                userActivityService.countDau(),
                userActivityService.countMau(),
                totalEvents,
                days,
                byType,
                daily,
                topSearches);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }
}
