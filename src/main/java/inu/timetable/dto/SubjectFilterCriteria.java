package inu.timetable.dto;

import inu.timetable.enums.SubjectType;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public record SubjectFilterCriteria(
        String semester,
        String subjectName,
        String professor,
        String courseCode,
        String department,
        List<String> departments,
        String dayOfWeek,
        Double startTime,
        Double endTime,
        SubjectType subjectType,
        Integer grade,
        Boolean isNight,
        Boolean unassignedTime,
        Integer credits,
        int page,
        int size) {

    public static SubjectFilterCriteria of(
            String semester,
            String subjectName,
            String professor,
            String courseCode,
            String department,
            List<String> departments,
            String dayOfWeek,
            Double startTime,
            Double endTime,
            SubjectType subjectType,
            Integer grade,
            Boolean isNight,
            Boolean unassignedTime,
            Integer credits,
            int page,
            int size) {
        return new SubjectFilterCriteria(
                trimToNull(semester),
                trimToNull(subjectName),
                trimToNull(professor),
                trimToNull(courseCode),
                normalizeDepartment(department),
                normalizeDepartments(departments),
                trimToNull(dayOfWeek),
                startTime,
                endTime,
                subjectType,
                grade,
                isNight,
                unassignedTime,
                credits,
                Math.max(0, page),
                Math.max(1, Math.min(size, 100)));
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String normalizeDepartment(String department) {
        String normalized = trimToNull(department);
        return "전체".equals(normalized) ? null : normalized;
    }

    private static List<String> normalizeDepartments(List<String> departments) {
        if (departments == null) {
            return List.of();
        }

        return departments.stream()
                .filter(Objects::nonNull)
                .flatMap(value -> Arrays.stream(value.split(",")))
                .map(String::trim)
                .filter(value -> !value.isBlank() && !"전체".equals(value))
                .distinct()
                .toList();
    }
}
