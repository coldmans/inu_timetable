package inu.timetable.repository;

import inu.timetable.entity.UserTimetable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserTimetableRepository extends JpaRepository<UserTimetable, Long> {

    interface SubjectTimetableAddCount {
        Long getSubjectId();
        Long getTimetableAddCount();
    }

    List<UserTimetable> findByUserIdAndSemester(Long userId, String semester);

    List<UserTimetable> findByUserId(Long userId);

    List<UserTimetable> findAllByUserIdAndSubjectId(Long userId, Long subjectId);

    // 학기까지 포함한 중복 검사(semester null 도 정확히 구분).
    @Query("SELECT COUNT(ut) > 0 FROM UserTimetable ut " +
           "WHERE ut.user.id = :userId AND ut.subject.id = :subjectId " +
           "AND ((:semester IS NULL AND ut.semester IS NULL) OR ut.semester = :semester)")
    boolean existsByUserIdAndSubjectIdAndSemester(@Param("userId") Long userId,
                                                  @Param("subjectId") Long subjectId,
                                                  @Param("semester") String semester);

    @Modifying
    @Query("DELETE FROM UserTimetable ut WHERE ut.user.id = :userId AND ut.subject.id = :subjectId")
    int deleteAllByUserIdAndSubjectId(@Param("userId") Long userId, @Param("subjectId") Long subjectId);
    
    @Query("SELECT DISTINCT ut FROM UserTimetable ut " +
           "JOIN FETCH ut.subject s " +
           "LEFT JOIN FETCH s.schedules " +
           "WHERE ut.user.id = :userId AND ut.semester = :semester")
    List<UserTimetable> findByUserIdAndSemesterWithSubjectAndSchedules(@Param("userId") Long userId, @Param("semester") String semester);

    // 시간 변경 충돌 검사용: 특정 과목을 해당 학기 시간표에 담아둔 모든 유저 항목을 조회한다.
    @Query("SELECT ut FROM UserTimetable ut JOIN FETCH ut.user u " +
           "WHERE ut.subject.id = :subjectId AND ut.semester = :semester")
    List<UserTimetable> findAllBySubjectIdAndSemesterWithUser(@Param("subjectId") Long subjectId,
                                                              @Param("semester") String semester);

    @Query("SELECT DISTINCT ut.user.id FROM UserTimetable ut " +
           "WHERE ut.subject.id IN :subjectIds AND ut.semester = :semester")
    List<Long> findDistinctUserIdsBySubjectIdsAndSemester(@Param("subjectIds") List<Long> subjectIds,
                                                          @Param("semester") String semester);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM UserTimetable ut " +
           "WHERE ut.subject.id IN :subjectIds AND ut.semester = :semester")
    int deleteAllBySubjectIdsAndSemester(@Param("subjectIds") List<Long> subjectIds,
                                         @Param("semester") String semester);

    @Query("SELECT ut.subject.id AS subjectId, COUNT(DISTINCT ut.user.id) AS timetableAddCount " +
           "FROM UserTimetable ut " +
           "WHERE ut.subject.id IN :subjectIds " +
           "GROUP BY ut.subject.id")
    List<SubjectTimetableAddCount> countAddedUsersBySubjectIds(@Param("subjectIds") List<Long> subjectIds);
}
