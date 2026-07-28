package inu.timetable.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 사이트 내 문의. resolved_at 이 NULL 이면 미처리 상태다.
 * 비로그인 문의를 허용하므로 user_id 는 NULL 일 수 있고,
 * 조회 시 User 연관을 로딩할 일이 없어 user_id 컬럼만 매핑한다(FK 는 DB 마이그레이션에서 보장).
 */
@Entity
@Table(
        name = "user_inquiries",
        indexes = @Index(name = "idx_user_inquiries_resolved_created", columnList = "resolved_at, created_at"))
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserInquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(length = 100)
    private String contact;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    public void resolve(LocalDateTime resolvedAt) {
        if (this.resolvedAt == null) {
            this.resolvedAt = resolvedAt;
        }
    }
}
