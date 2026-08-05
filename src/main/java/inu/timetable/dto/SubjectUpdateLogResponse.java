package inu.timetable.dto;

import inu.timetable.entity.SubjectUpdateLog;

import java.time.Instant;

public record SubjectUpdateLogResponse(
        Instant appliedAt,
        String semester,
        String sourceFormat,
        int addedCount,
        int modifiedCount,
        int removedCount) {

    public static SubjectUpdateLogResponse fromEntity(SubjectUpdateLog log) {
        return new SubjectUpdateLogResponse(
                log.getAppliedAt(),
                log.getSemester(),
                log.getSourceFormat(),
                log.getAddedCount(),
                log.getModifiedCount(),
                log.getRemovedCount());
    }
}
