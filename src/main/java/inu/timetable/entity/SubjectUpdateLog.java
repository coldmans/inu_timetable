package inu.timetable.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 공식 엑셀 반영 이력(공개 일지). apply 성공 시마다 1행 기록한다.
 */
@Entity
@Table(name = "subject_update_logs")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubjectUpdateLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "applied_at", nullable = false)
    private LocalDateTime appliedAt;

    @Column(nullable = false, length = 20)
    private String semester;

    @Column(name = "source_format", length = 32)
    private String sourceFormat;

    @Column(name = "added_count", nullable = false)
    private Integer addedCount;

    @Column(name = "modified_count", nullable = false)
    private Integer modifiedCount;

    @Column(name = "removed_count", nullable = false)
    private Integer removedCount;
}
