package inu.timetable.repository;

import inu.timetable.entity.Subject;
import inu.timetable.enums.ClassMethod;
import inu.timetable.enums.SubjectType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubjectRepository extends JpaRepository<Subject, Long> {

        // 시간 블록 완전 포함 필터.
        // 과목의 모든 스케줄이 요일별 선택 구간 안에 완전히 포함되어야 한다(위반 스케줄이 하나도 없어야 함).
        // 스케줄이 없는 과목(온라인 등)은 NOT EXISTS 가 자동으로 만족되어 포함된다.
        String TIME_BLOCK_CONTAINMENT_CLAUSE = " AND (:timeBlocksActive = false OR NOT EXISTS (" +
                        "SELECT 1 FROM Schedule vs WHERE vs.subject = s AND NOT (" +
                        "(vs.dayOfWeek = '월' AND :monStart IS NOT NULL AND vs.startTime >= :monStart AND vs.endTime <= :monEnd) " +
                        "OR (vs.dayOfWeek = '화' AND :tueStart IS NOT NULL AND vs.startTime >= :tueStart AND vs.endTime <= :tueEnd) " +
                        "OR (vs.dayOfWeek = '수' AND :wedStart IS NOT NULL AND vs.startTime >= :wedStart AND vs.endTime <= :wedEnd) " +
                        "OR (vs.dayOfWeek = '목' AND :thuStart IS NOT NULL AND vs.startTime >= :thuStart AND vs.endTime <= :thuEnd) " +
                        "OR (vs.dayOfWeek = '금' AND :friStart IS NOT NULL AND vs.startTime >= :friStart AND vs.endTime <= :friEnd) " +
                        "OR (vs.dayOfWeek = '토' AND :satStart IS NOT NULL AND vs.startTime >= :satStart AND vs.endTime <= :satEnd)" +
                        "))) ";

        List<Subject> findBySubjectType(SubjectType subjectType);

        List<Subject> findBySubjectTypeAndActiveTrue(SubjectType subjectType);

        List<Subject> findByProfessor(String professor);

        List<Subject> findByProfessorAndActiveTrue(String professor);

        List<Subject> findBySubjectNameContaining(String keyword);

        List<Subject> findBySubjectNameContainingAndActiveTrue(String keyword);

        List<Subject> findBySubjectNameContainingAndGrade(String keyword, Integer grade);

        List<Subject> findBySubjectNameContainingAndGradeAndActiveTrue(String keyword, Integer grade);

        List<Subject> findByProfessorContaining(String keyword);

        List<Subject> findByProfessorContainingAndActiveTrue(String keyword);

        List<Subject> findByProfessorContainingAndGrade(String keyword, Integer grade);

        List<Subject> findByProfessorContainingAndGradeAndActiveTrue(String keyword, Integer grade);

        List<Subject> findByCredits(Integer credits);

        List<Subject> findByGrade(Integer grade);

        List<Subject> findByGradeAndActiveTrue(Integer grade);

        List<Subject> findByDepartment(String department);

        List<Subject> findByDepartmentAndActiveTrue(String department);

        List<Subject> findByGradeAndDepartment(Integer grade, String department);

        List<Subject> findBySubjectTypeAndGrade(SubjectType subjectType, Integer grade);

        List<Subject> findBySubjectTypeAndDepartment(SubjectType subjectType, String department);

        Page<Subject> findByActiveTrue(Pageable pageable);

        Optional<Subject> findFirstByCourseCodeAndSemesterOrderByIdAsc(String courseCode, String semester);

        @Query(value = "SELECT s FROM Subject s " +
                        "WHERE s.active = true AND (s.semester = :semester OR s.semester IS NULL)",
                        countQuery = "SELECT count(s) FROM Subject s " +
                                        "WHERE s.active = true AND (s.semester = :semester OR s.semester IS NULL)")
        Page<Subject> findActiveSeedCandidatesBySemester(@Param("semester") String semester, Pageable pageable);

        long countByActiveTrue();

        @Query(value = "SELECT s.id FROM Subject s " +
                        "LEFT JOIN s.schedules sch " +
                        "LEFT JOIN UserTimetable ut ON ut.subject = s " +
                        "WHERE s.active = true " +
                        "AND (:semester IS NULL OR s.semester = :semester OR s.semester IS NULL) " +
                        "AND (:subjectName IS NULL OR LOWER(s.subjectName) LIKE LOWER(CONCAT('%', CAST(:subjectName AS string), '%'))) " +
                        "AND (:professor IS NULL OR LOWER(s.professor) LIKE LOWER(CONCAT('%', CAST(:professor AS string), '%'))) " +
                        "AND (:courseCode IS NULL OR LOWER(s.courseCode) LIKE LOWER(CONCAT('%', CAST(:courseCode AS string), '%'))) " +
                        "AND (:department IS NULL OR s.department = :department) " +
                        "AND (:departmentCount = 0 OR s.department IN :departments) " +
                        "AND (:subjectType IS NULL OR s.subjectType = :subjectType) " +
                        "AND (:grade IS NULL OR s.grade = :grade) " +
                        "AND (:isNight IS NULL OR s.isNight = :isNight) " +
                        "AND (:credits IS NULL OR s.credits = :credits) " +
                        "AND (:unassignedTime IS NULL OR :unassignedTime = false OR " +
                        "(:unassignedTime = true AND (s.classMethod = :onlineClassMethod OR sch.id IS NULL))) " +
                        "AND (:dayOfWeek IS NULL OR sch.dayOfWeek = :dayOfWeek) " +
                        "AND (:startTime IS NULL OR sch.startTime >= :startTime) " +
                        "AND (:endTime IS NULL OR sch.endTime <= :endTime) " +
                        TIME_BLOCK_CONTAINMENT_CLAUSE +
                        "GROUP BY s.id " +
                        "ORDER BY COUNT(DISTINCT ut.user.id) DESC, s.id ASC", countQuery = "SELECT count(DISTINCT s.id) FROM Subject s LEFT JOIN s.schedules sch "
                                        +
                                        "WHERE s.active = true " +
                                        "AND (:semester IS NULL OR s.semester = :semester OR s.semester IS NULL) " +
                                        "AND (:subjectName IS NULL OR LOWER(s.subjectName) LIKE LOWER(CONCAT('%', CAST(:subjectName AS string), '%'))) " +
                                        "AND (:professor IS NULL OR LOWER(s.professor) LIKE LOWER(CONCAT('%', CAST(:professor AS string), '%'))) " +
                                        "AND (:courseCode IS NULL OR LOWER(s.courseCode) LIKE LOWER(CONCAT('%', CAST(:courseCode AS string), '%'))) " +
                                        "AND (:department IS NULL OR s.department = :department) " +
                                        "AND (:departmentCount = 0 OR s.department IN :departments) " +
                                        "AND (:subjectType IS NULL OR s.subjectType = :subjectType) " +
                                        "AND (:grade IS NULL OR s.grade = :grade) " +
                                        "AND (:isNight IS NULL OR s.isNight = :isNight) " +
                                        "AND (:credits IS NULL OR s.credits = :credits) " +
                                        "AND (:unassignedTime IS NULL OR :unassignedTime = false OR " +
                                        "(:unassignedTime = true AND (s.classMethod = :onlineClassMethod OR sch.id IS NULL))) " +
                                        "AND (:dayOfWeek IS NULL OR sch.dayOfWeek = :dayOfWeek) " +
                                        "AND (:startTime IS NULL OR sch.startTime >= :startTime) " +
                                        "AND (:endTime IS NULL OR sch.endTime <= :endTime)" +
                                        TIME_BLOCK_CONTAINMENT_CLAUSE)
        Page<Long> findIdsWithFilters(
                        @Param("semester") String semester,
                        @Param("subjectName") String subjectName,
                        @Param("professor") String professor,
                        @Param("courseCode") String courseCode,
                        @Param("department") String department,
                        @Param("departments") List<String> departments,
                        @Param("departmentCount") int departmentCount,
                        @Param("dayOfWeek") String dayOfWeek,
                        @Param("startTime") Double startTime,
                        @Param("endTime") Double endTime,
                        @Param("subjectType") SubjectType subjectType,
                        @Param("grade") Integer grade,
                        @Param("isNight") Boolean isNight,
                        @Param("credits") Integer credits,
                        @Param("unassignedTime") Boolean unassignedTime,
                        @Param("onlineClassMethod") ClassMethod onlineClassMethod,
                        @Param("timeBlocksActive") boolean timeBlocksActive,
                        @Param("monStart") Double monStart,
                        @Param("monEnd") Double monEnd,
                        @Param("tueStart") Double tueStart,
                        @Param("tueEnd") Double tueEnd,
                        @Param("wedStart") Double wedStart,
                        @Param("wedEnd") Double wedEnd,
                        @Param("thuStart") Double thuStart,
                        @Param("thuEnd") Double thuEnd,
                        @Param("friStart") Double friStart,
                        @Param("friEnd") Double friEnd,
                        @Param("satStart") Double satStart,
                        @Param("satEnd") Double satEnd,
                        Pageable pageable);

        @Query("SELECT DISTINCT s FROM Subject s LEFT JOIN FETCH s.schedules WHERE s.active = true AND s.id IN :subjectIds")
        List<Subject> findWithSchedulesByIds(@Param("subjectIds") List<Long> subjectIds);

        @Query("SELECT DISTINCT s FROM Subject s LEFT JOIN FETCH s.schedules WHERE s.id = :subjectId")
        Optional<Subject> findWithSchedulesById(@Param("subjectId") Long subjectId);

        @Query("SELECT DISTINCT s.department FROM Subject s WHERE s.active = true AND s.department IS NOT NULL ORDER BY s.department")
        List<String> findDistinctDepartments();

        @Query("SELECT DISTINCT s.department FROM Subject s " +
                        "WHERE s.active = true " +
                        "AND (s.semester = :semester OR s.semester IS NULL) " +
                        "AND s.department IS NOT NULL " +
                        "ORDER BY s.department")
        List<String> findDistinctDepartmentsBySemester(@Param("semester") String semester);

        @Query("SELECT DISTINCT s.grade FROM Subject s WHERE s.active = true AND s.grade IS NOT NULL ORDER BY s.grade")
        List<Integer> findDistinctGrades();

        // 관리자 학기 필터용. 비활성 과목 포함 전체 학기를 최신순으로 반환한다.
        @Query("SELECT DISTINCT s.semester FROM Subject s WHERE s.semester IS NOT NULL ORDER BY s.semester DESC")
        List<String> findDistinctSemesters();

        @Query("SELECT DISTINCT s FROM Subject s LEFT JOIN FETCH s.schedules")
        List<Subject> findAllWithSchedules();

        @Query("SELECT DISTINCT s FROM Subject s LEFT JOIN FETCH s.schedules " +
                        "WHERE (s.semester = :semester AND s.courseCode IS NOT NULL) " +
                        "OR s.courseCode IS NULL")
        List<Subject> findImportCandidatesBySemester(@Param("semester") String semester);
}
