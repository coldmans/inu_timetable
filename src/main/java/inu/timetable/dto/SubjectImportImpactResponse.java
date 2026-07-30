package inu.timetable.dto;

import java.util.List;

public record SubjectImportImpactResponse(
        int selectedCourseCount,
        int timetableUsers,
        int wishlistUsers,
        int totalUsers,
        int conflictUsers,
        int conflictEntries,
        int cancellationTimetableEntries,
        List<ProjectedConflict> conflicts,
        boolean conflictsTruncated) {

    public record ProjectedConflict(
            Long userId,
            String courseCode,
            String subjectName,
            String conflictingCourseCode,
            String conflictingSubjectName) {
    }
}
