package inu.timetable.repository;

import inu.timetable.entity.Subject;
import inu.timetable.enums.SubjectType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubjectRepository extends JpaRepository<Subject, Long> {

    List<Subject> findBySubjectType(SubjectType subjectType);

    List<Subject> findByProfessor(String professor);

    List<Subject> findBySubjectNameContaining(String keyword);

    List<Subject> findBySubjectNameContainingAndGrade(String keyword, Integer grade);

    List<Subject> findByProfessorContaining(String keyword);

    List<Subject> findByProfessorContainingAndGrade(String keyword, Integer grade);

    List<Subject> findByCredits(Integer credits);

    List<Subject> findByGrade(Integer grade);

    List<Subject> findByDepartment(String department);

    List<Subject> findByGradeAndDepartment(Integer grade, String department);

    List<Subject> findBySubjectTypeAndGrade(SubjectType subjectType, Integer grade);

    List<Subject> findBySubjectTypeAndDepartment(SubjectType subjectType, String department);

    @Query(value = "SELECT DISTINCT s.id FROM Subject s LEFT JOIN s.schedules sch " +
            "WHERE (:subjectName IS NULL OR s.subjectName LIKE %:subjectName%) " +
            "AND (:professor IS NULL OR s.professor LIKE %:professor%) " +
            "AND (:department IS NULL OR s.department LIKE %:department%) " +
            "AND (:subjectType IS NULL OR s.subjectType = :subjectType) " +
            "AND (:grade IS NULL OR s.grade = :grade) " +
            "AND (:isNight IS NULL OR s.isNight = :isNight) " +
            "AND (:dayOfWeek IS NULL OR sch.dayOfWeek = :dayOfWeek) " +
            "AND (:startTime IS NULL OR sch.startTime >= :startTime) " +
            "AND (:endTime IS NULL OR sch.endTime <= :endTime)", countQuery = "SELECT count(DISTINCT s.id) FROM Subject s LEFT JOIN s.schedules sch "
                    +
                    "WHERE (:subjectName IS NULL OR s.subjectName LIKE :subjectName) " +
                    "AND (:professor IS NULL OR s.professor LIKE %:professor%) " +
                    "AND (:department IS NULL OR s.department LIKE %:department%) " +
                    "AND (:subjectType IS NULL OR s.subjectType = :subjectType) " +
                    "AND (:grade IS NULL OR s.grade = :grade) " +
                    "AND (:isNight IS NULL OR s.isNight = :isNight) " +
                    "AND (:dayOfWeek IS NULL OR sch.dayOfWeek = :dayOfWeek) " +
                    "AND (:startTime IS NULL OR sch.startTime >= :startTime) " +
                    "AND (:endTime IS NULL OR sch.endTime <= :endTime)")
    Page<Long> findIdsWithFilters(
            @Param("subjectName") String subjectName,
            @Param("professor") String professor,
            @Param("department") String department,
            @Param("dayOfWeek") String dayOfWeek,
            @Param("startTime") Double startTime,
            @Param("endTime") Double endTime,
            @Param("subjectType") SubjectType subjectType,
            @Param("grade") Integer grade,
            @Param("isNight") Boolean isNight,
            Pageable pageable);

    @Query("SELECT DISTINCT s FROM Subject s LEFT JOIN FETCH s.schedules WHERE s.id IN :subjectIds")
    List<Subject> findWithSchedulesByIds(@Param("subjectIds") List<Long> subjectIds);

    @Query("SELECT DISTINCT s.department FROM Subject s WHERE s.department IS NOT NULL ORDER BY s.department")
    List<String> findDistinctDepartments();

    @Query("SELECT DISTINCT s.grade FROM Subject s WHERE s.grade IS NOT NULL ORDER BY s.grade")
    List<Integer> findDistinctGrades();

    @Query("SELECT DISTINCT s FROM Subject s LEFT JOIN FETCH s.schedules")
    List<Subject> findAllWithSchedules();
}
