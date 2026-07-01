package inu.timetable.entity;

import inu.timetable.enums.AnalyticsEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사용자 행동 이벤트 로그(자체 제품 분석용).
 * 로그 성격이라 User 와 FK 로 묶지 않고 userId(Long, nullable)만 저장한다
 * (비로그인 사용자 허용 + 대량 삽입/사용자 삭제와의 독립성).
 */
@Entity
@Table(
        name = "analytics_events",
        indexes = {
                @Index(name = "idx_analytics_events_type_time", columnList = "event_type, occurred_at"),
                @Index(name = "idx_analytics_events_occurred", columnList = "occurred_at")
        })
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalyticsEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private AnalyticsEventType eventType;

    @Column(name = "user_id")
    private Long userId;

    // 검색어, 과목명 등 이벤트별 부가 라벨(선택).
    @Column(name = "label", length = 255)
    private String label;

    // 비로그인 사용자 구분용 클라이언트 세션 식별자(선택).
    @Column(name = "session_id", length = 64)
    private String sessionId;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;
}
