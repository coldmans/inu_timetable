package inu.timetable.repository;

import inu.timetable.entity.AnalyticsEvent;
import inu.timetable.enums.AnalyticsEventType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AnalyticsEventRepository extends JpaRepository<AnalyticsEvent, Long> {

    interface EventTypeCount {
        AnalyticsEventType getEventType();
        Long getCount();
    }

    interface DailyCount {
        LocalDate getDay();
        Long getCount();
    }

    interface LabelCount {
        String getLabel();
        Long getCount();
    }

    @Query("SELECT e.eventType AS eventType, COUNT(e) AS count " +
           "FROM AnalyticsEvent e WHERE e.occurredAt >= :from " +
           "GROUP BY e.eventType ORDER BY COUNT(e) DESC")
    List<EventTypeCount> countByType(@Param("from") LocalDateTime from);

    @Query("SELECT CAST(e.occurredAt AS date) AS day, COUNT(e) AS count " +
           "FROM AnalyticsEvent e WHERE e.occurredAt >= :from " +
           "GROUP BY CAST(e.occurredAt AS date) ORDER BY CAST(e.occurredAt AS date)")
    List<DailyCount> countDaily(@Param("from") LocalDateTime from);

    @Query("SELECT e.label AS label, COUNT(e) AS count " +
           "FROM AnalyticsEvent e " +
           "WHERE e.eventType = :type AND e.label IS NOT NULL AND e.label <> '' AND e.occurredAt >= :from " +
           "GROUP BY e.label ORDER BY COUNT(e) DESC")
    List<LabelCount> topLabels(@Param("type") AnalyticsEventType type,
                               @Param("from") LocalDateTime from,
                               Pageable pageable);

    long countByOccurredAtGreaterThanEqual(LocalDateTime from);
}
