package inu.timetable.dto;

import java.util.List;

public record TimetableReconciliationResult(
        int conflictRemovedCount,
        int deactivatedRemovedCount,
        int notifiedUserCount,
        int timetableUserCount,
        int wishlistUserCount,
        int affectedUserCount,
        List<RemovedTimetableEntry> removedEntries) {

    public record RemovedTimetableEntry(
            Long timetableEntryId,
            Long userId,
            Long subjectId,
            String courseCode,
            String subjectName,
            String reason) {
    }
}
