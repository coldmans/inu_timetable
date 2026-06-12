package inu.timetable.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class OfficialSubjectImportResponse {

    private boolean applied;
    private String semester;
    private int totalRows;
    private int addedCount;
    private int modifiedCount;
    private int removedCount;
    private int unchangedCount;
    private List<SubjectImportItem> addedSubjects;
    private List<ModifiedSubjectImportItem> modifiedSubjects;
    private List<SubjectImportItem> removedSubjects;
    private List<String> warnings;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class SubjectImportItem {
        private Long id;
        private String courseCode;
        private String subjectName;
        private String professor;
        private String department;
        private Integer grade;
        private String subjectType;
        private Integer credits;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class ModifiedSubjectImportItem {
        private Long id;
        private String courseCode;
        private String subjectName;
        private String professor;
        private String department;
        private Integer grade;
        private String subjectType;
        private Integer credits;
        private List<String> changedFields;
    }
}
