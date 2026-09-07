package inu.timetable.dto;

import inu.timetable.enums.SubjectImportChangeCategory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public record SubjectImportPlanResponse(
        String planId,
        String status,
        String semester,
        String sourceFormat,
        String sourceFilename,
        int totalRows,
        int changedCount,
        int unchangedCount,
        boolean deactivateMissing,
        LocalDateTime createdAt,
        List<ChangeItem> changes,
        SubjectImportImpactResponse impact,
        List<String> warnings) {

    public record ChangeItem(
            String courseCode,
            String subjectName,
            Set<SubjectImportChangeCategory> categories,
            List<FieldChange> fields,
            SubjectSnapshot before,
            SubjectSnapshot after,
            CourseImpact impact) {
    }

    public record FieldChange(String field, String before, String after) {
    }

    public record CourseImpact(int timetableUsers, int wishlistUsers) {
    }

    public record SubjectSnapshot(
            Long id,
            String courseCode,
            String semester,
            boolean active,
            String subjectName,
            Integer credits,
            String professor,
            String department,
            Integer grade,
            String subjectType,
            String classMethod,
            boolean night,
            boolean syllabusAvailable,
            List<ScheduleSnapshot> schedules) {
    }

    public record ScheduleSnapshot(
            String dayOfWeek,
            Double startTime,
            Double endTime,
            List<RoomSnapshot> rooms) {
    }

    public record RoomSnapshot(String room, Double startTime, Double endTime) {
    }
}
