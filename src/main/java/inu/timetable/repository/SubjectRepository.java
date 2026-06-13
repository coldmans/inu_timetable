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

        @Query(value = "SELECT s FROM Subject s " +
                        "WHERE s.active = true AND (s.semester = :semester OR s.semester IS NULL)",
                        countQuery = "SELECT count(s) FROM Subject s " +
                                        "WHERE s.active = true AND (s.semester = :semester OR s.semester IS NULL)")
        Page<Subject> findActiveSeedCandidatesBySemester(@Param("semester") String semester, Pageable pageable);

        long countByActiveTrue();

        @Query(value = "SELECT DISTINCT s.id FROM Subject s LEFT JOIN s.schedules sch " +
                        "WHERE s.active = true " +
                        "AND (:subjectName IS NULL OR s.subjectName LIKE %:subjectName%) " +
                        "AND (:professor IS NULL OR s.professor LIKE %:professor%) " +
                        "AND (:department IS NULL OR s.department LIKE %:department%) " +
                        "AND (:departmentCount = 0 OR s.department IN :departments) " +
                        "AND (:subjectType IS NULL OR s.subjectType = :subjectType) " +
                        "AND (:grade IS NULL OR s.grade = :grade) " +
                        "AND (:isNight IS NULL OR s.isNight = :isNight) " +
                        "AND (:credits IS NULL OR s.credits = :credits) " +
                        "AND (:unassignedTime IS NULL OR :unassignedTime = false OR " +
                        "(:unassignedTime = true AND (s.classMethod = :onlineClassMethod OR sch.id IS NULL))) " +
                        "AND (:dayOfWeek IS NULL OR sch.dayOfWeek = :dayOfWeek) " +
                        "AND (:startTime IS NULL OR sch.startTime >= :startTime) " +
                        "AND (:endTime IS NULL OR sch.endTime <= :endTime)", countQuery = "SELECT count(DISTINCT s.id) FROM Subject s LEFT JOIN s.schedules sch "
                                        +
                                        "WHERE s.active = true " +
                                        "AND (:subjectName IS NULL OR s.subjectName LIKE %:subjectName%) " +
                                        "AND (:professor IS NULL OR s.professor LIKE %:professor%) " +
                                        "AND (:department IS NULL OR s.department LIKE %:department%) " +
                                        "AND (:departmentCount = 0 OR s.department IN :departments) " +
                                        "AND (:subjectType IS NULL OR s.subjectType = :subjectType) " +
                                        "AND (:grade IS NULL OR s.grade = :grade) " +
                                        "AND (:isNight IS NULL OR s.isNight = :isNight) " +
                                        "AND (:credits IS NULL OR s.credits = :credits) " +
                                        "AND (:unassignedTime IS NULL OR :unassignedTime = false OR " +
                                        "(:unassignedTime = true AND (s.classMethod = :onlineClassMethod OR sch.id IS NULL))) " +
                                        "AND (:dayOfWeek IS NULL OR sch.dayOfWeek = :dayOfWeek) " +
                                        "AND (:startTime IS NULL OR sch.startTime >= :startTime) " +
                                        "AND (:endTime IS NULL OR sch.endTime <= :endTime)")
        Page<Long> findIdsWithFilters(
                        @Param("subjectName") String subjectName,
                        @Param("professor") String professor,
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
                        Pageable pageable);

        @Query("SELECT DISTINCT s FROM Subject s LEFT JOIN FETCH s.schedules WHERE s.active = true AND s.id IN :subjectIds")
        List<Subject> findWithSchedulesByIds(@Param("subjectIds") List<Long> subjectIds);

        @Query("SELECT DISTINCT s FROM Subject s LEFT JOIN FETCH s.schedules WHERE s.id = :subjectId")
        Optional<Subject> findWithSchedulesById(@Param("subjectId") Long subjectId);

        @Query("SELECT DISTINCT s.department FROM Subject s WHERE s.active = true AND s.department IS NOT NULL ORDER BY s.department")
        List<String> findDistinctDepartments();

        @Query("SELECT DISTINCT s.grade FROM Subject s WHERE s.active = true AND s.grade IS NOT NULL ORDER BY s.grade")
        List<Integer> findDistinctGrades();

        @Query("SELECT DISTINCT s FROM Subject s LEFT JOIN FETCH s.schedules")
        List<Subject> findAllWithSchedules();

        @Query("SELECT DISTINCT s FROM Subject s LEFT JOIN FETCH s.schedules " +
                        "WHERE (s.semester = :semester AND s.courseCode IS NOT NULL) " +
                        "OR s.courseCode IS NULL")
        List<Subject> findImportCandidatesBySemester(@Param("semester") String semester);
}
