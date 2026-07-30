package inu.timetable.enums;

public enum SubjectImportChangeCategory {
    ADDED("강의 추가"),
    CANCELED("폐강"),
    TIME_CHANGED("시간 변경"),
    ROOM_CHANGED("강의실 변경"),
    SYLLABUS_ATTACHED("강의계획서 첨부"),
    SYLLABUS_REMOVED("강의계획서 미첨부"),
    REACTIVATED("재개설"),
    DETAILS_CHANGED("기타 정보 변경");

    private final String label;

    SubjectImportChangeCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
