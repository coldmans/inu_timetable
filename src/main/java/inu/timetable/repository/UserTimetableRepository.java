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
    
    List<UserTimetable> findByUserIdAndSemester(Long userId, String semester);
    
    List<UserTimetable> findByUserId(Long userId);
    
    Optional<UserTimetable> findByUserIdAndSubjectId(Long userId, Long subjectId);
    
    @Query("SELECT ut FROM UserTimetable ut JOIN FETCH ut.subject s JOIN FETCH s.schedules WHERE ut.user.id = :userId AND ut.semester = :semester")
    List<UserTimetable> findByUserIdAndSemesterWithSubjectAndSchedules(@Param("userId") Long userId, @Param("semester") String semester);
}