package inu.timetable.repository;

import inu.timetable.entity.UserTimetable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserTimetableRepository extends JpaRepository<UserTimetable, Long> {

    interface SubjectTimetableAddCount {
        Long getSubjectId();
        Long getTimetableAddCount();
    }
    
    List<UserTimetable> findByUserIdAndSemester(Long userId, String semester);
    
    List<UserTimetable> findByUserId(Long userId);
    
    Optional<UserTimetable> findByUserIdAndSubjectId(Long userId, Long subjectId);
    
    @Query("SELECT DISTINCT ut FROM UserTimetable ut JOIN FETCH ut.subject s WHERE ut.user.id = :userId AND ut.semester = :semester")
    List<UserTimetable> findByUserIdAndSemesterWithSubjectAndSchedules(@Param("userId") Long userId, @Param("semester") String semester);

    @Query("SELECT ut.subject.id AS subjectId, COUNT(DISTINCT ut.user.id) AS timetableAddCount " +
           "FROM UserTimetable ut " +
           "WHERE ut.subject.id IN :subjectIds " +
           "GROUP BY ut.subject.id")
    List<SubjectTimetableAddCount> countAddedUsersBySubjectIds(@Param("subjectIds") List<Long> subjectIds);
}
