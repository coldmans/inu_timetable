package inu.timetable.repository;

import inu.timetable.entity.UserTimetable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserTimetableRepository extends JpaRepository<UserTimetable, Long> {
    
    List<UserTimetable> findByUserIdAndSemester(Long userId, String semester);
    
    List<UserTimetable> findByUserId(Long userId);
    
    Optional<UserTimetable> findByUserIdAndSubjectIdAndSemester(Long userId, Long subjectId, String semester);
    
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM UserTimetable ut WHERE ut.user.id = :userId AND ut.subject.id = :subjectId AND ut.semester = :semester")
    int deleteByUserIdAndSubjectIdAndSemester(@Param("userId") Long userId, @Param("subjectId") Long subjectId, @Param("semester") String semester);

    @Query("SELECT DISTINCT ut FROM UserTimetable ut JOIN FETCH ut.subject s WHERE ut.user.id = :userId AND ut.semester = :semester")
    List<UserTimetable> findByUserIdAndSemesterWithSubjectAndSchedules(@Param("userId") Long userId, @Param("semester") String semester);
}