package inu.timetable.service;

import java.util.List;

public final class SubjectCacheNames {

    public static final String ACTIVE_SUBJECT_COUNT = "activeSubjectCount";
    public static final String SUBJECT_FILTERS = "subjectFilters";
    public static final String SUBJECT_NAME_SEARCH = "subjectNameSearch";
    public static final String SUBJECT_PROFESSOR_SEARCH = "subjectProfessorSearch";
    public static final String SUBJECT_DEPARTMENTS = "subjectDepartments";
    public static final String SUBJECT_GRADES = "subjectGrades";

    public static final List<String> ALL = List.of(
            ACTIVE_SUBJECT_COUNT,
            SUBJECT_FILTERS,
            SUBJECT_NAME_SEARCH,
            SUBJECT_PROFESSOR_SEARCH,
            SUBJECT_DEPARTMENTS,
            SUBJECT_GRADES);

    private SubjectCacheNames() {
    }
}
