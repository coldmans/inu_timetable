package inu.timetable.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "subject_import_plans")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubjectImportPlan {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 20)
    private String semester;

    @Column(name = "source_format", nullable = false, length = 32)
    private String sourceFormat;

    @Column(name = "source_filename")
    private String sourceFilename;

    @Column(name = "source_sha256", nullable = false, length = 64)
    private String sourceSha256;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "deactivate_missing", nullable = false)
    private Boolean deactivateMissing;

    @Column(name = "total_rows", nullable = false)
    private Integer totalRows;

    @Column(name = "unchanged_count", nullable = false)
    private Integer unchangedCount;

    @Column(name = "plan_payload", nullable = false, columnDefinition = "TEXT")
    private String planPayload;

    @Column(name = "apply_result", columnDefinition = "TEXT")
    private String applyResult;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;
}
