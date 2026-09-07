package inu.timetable.dto;

public record SubjectSearchCriteria(String keyword, Integer grade) {

    public static SubjectSearchCriteria of(String keyword, Integer grade) {
        return new SubjectSearchCriteria(trim(keyword), grade);
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        return value.trim();
    }
}
