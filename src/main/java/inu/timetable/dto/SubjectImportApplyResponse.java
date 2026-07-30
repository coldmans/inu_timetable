package inu.timetable.dto;

import inu.timetable.service.TimetableConflictResolutionService;

import java.time.LocalDateTime;
import java.util.List;

public record SubjectImportApplyResponse(
        String planId,
        boolean applied,
        boolean verified,
        LocalDateTime appliedAt,
        int selectedCourseCount,
        int addedCount,
        int modifiedCount,
        int canceledCount,
        SubjectImportImpactResponse previewedImpact,
        TimetableConflictResolutionService.ReconciliationResult reconciliation,
        List<VerificationItem> verification) {

    public record VerificationItem(String courseCode, boolean matched, String message) {
    }
}
