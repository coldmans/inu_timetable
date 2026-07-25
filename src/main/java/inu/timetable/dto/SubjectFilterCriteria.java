package inu.timetable.dto;

import inu.timetable.enums.SubjectType;
import inu.timetable.exception.ApiException;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

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
        List<String> timeBlocks,
        int page,
        int size) {

    private static final List<String> TIME_BLOCK_DAY_ORDER = List.of("월", "화", "수", "목", "금", "토");
    private static final Pattern TIME_BLOCK_PATTERN =
            Pattern.compile("^([월화수목금토]):(\\d+(?:\\.\\d+)?)-(\\d+(?:\\.\\d+)?)$");

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
            List<String> timeBlocks,
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
                normalizeTimeBlocks(timeBlocks),
                Math.max(0, page),
                Math.max(1, Math.min(size, 100)));
    }

    /**
     * 요일별 시간 블록 구간을 리포지토리 파라미터로 풀어낸 값.
     * start/end 는 교시 단위(Double)이며, 선택하지 않은 요일은 null 이다.
     */
    public record TimeBlockParams(
            boolean active,
            Double monStart, Double monEnd,
            Double tueStart, Double tueEnd,
            Double wedStart, Double wedEnd,
            Double thuStart, Double thuEnd,
            Double friStart, Double friEnd,
            Double satStart, Double satEnd) {

        public static TimeBlockParams inactive() {
            return new TimeBlockParams(false,
                    null, null, null, null, null, null,
                    null, null, null, null, null, null);
        }
    }

    public TimeBlockParams toTimeBlockParams() {
        if (timeBlocks.isEmpty()) {
            return TimeBlockParams.inactive();
        }
        Double[] ranges = new Double[TIME_BLOCK_DAY_ORDER.size() * 2];
        for (String block : timeBlocks) {
            var matcher = TIME_BLOCK_PATTERN.matcher(block);
            if (!matcher.matches()) {
                throw invalidTimeBlock(block);
            }
            int dayIndex = TIME_BLOCK_DAY_ORDER.indexOf(matcher.group(1));
            ranges[dayIndex * 2] = Double.parseDouble(matcher.group(2));
            ranges[dayIndex * 2 + 1] = Double.parseDouble(matcher.group(3));
        }
        return new TimeBlockParams(true,
                ranges[0], ranges[1], ranges[2], ranges[3], ranges[4], ranges[5],
                ranges[6], ranges[7], ranges[8], ranges[9], ranges[10], ranges[11]);
    }

    private static List<String> normalizeTimeBlocks(List<String> timeBlocks) {
        if (timeBlocks == null) {
            return List.of();
        }
        List<String> normalized = timeBlocks.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
        long distinctDays = normalized.stream()
                .map(SubjectFilterCriteria::validateTimeBlock)
                .distinct()
                .count();
        if (distinctDays < normalized.size()) {
            throw ApiException.badRequest("timeBlocks에 같은 요일을 중복해서 지정할 수 없습니다.");
        }
        return normalized.stream()
                .sorted((a, b) -> Integer.compare(
                        TIME_BLOCK_DAY_ORDER.indexOf(a.substring(0, 1)),
                        TIME_BLOCK_DAY_ORDER.indexOf(b.substring(0, 1))))
                .toList();
    }

    private static String validateTimeBlock(String block) {
        var matcher = TIME_BLOCK_PATTERN.matcher(block);
        if (!matcher.matches()) {
            throw invalidTimeBlock(block);
        }
        double start = Double.parseDouble(matcher.group(2));
        double end = Double.parseDouble(matcher.group(3));
        if (start > end) {
            throw invalidTimeBlock(block);
        }
        return matcher.group(1);
    }

    private static ApiException invalidTimeBlock(String block) {
        return ApiException.badRequest(
                "timeBlocks 형식이 올바르지 않습니다: '" + block + "' (예: 수:4-10)");
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
