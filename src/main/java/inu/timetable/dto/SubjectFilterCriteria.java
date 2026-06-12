package inu.timetable.dto;

import inu.timetable.enums.SubjectType;

public record SubjectFilterCriteria(
        String subjectName,
        String professor,
        String department,
        String dayOfWeek,
        Double startTime,
        Double endTime,
        SubjectType subjectType,
        Integer grade,
        Boolean isNight,
        Integer credits,
        int page,
        int size) {

    public static SubjectFilterCriteria of(
            String subjectName,
            String professor,
            String department,
            String dayOfWeek,
            Double startTime,
            Double endTime,
            SubjectType subjectType,
            Integer grade,
            Boolean isNight,
            Integer credits,
            int page,
            int size) {
        return new SubjectFilterCriteria(
                trimToNull(subjectName),
                trimToNull(professor),
                trimToNull(department),
                trimToNull(dayOfWeek),
                startTime,
                endTime,
                subjectType,
                grade,
                isNight,
                credits,
                page,
                size);
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
