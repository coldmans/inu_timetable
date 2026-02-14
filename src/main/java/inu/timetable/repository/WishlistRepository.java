package inu.timetable.repository;

import inu.timetable.entity.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WishlistRepository extends JpaRepository<WishlistItem, Long> {
    
    List<WishlistItem> findByUserIdAndSemester(Long userId, String semester);
    
    @Query("SELECT DISTINCT w FROM WishlistItem w " +
           "JOIN FETCH w.subject s " +
           "LEFT JOIN FETCH s.schedules " +
           "WHERE w.user.id = :userId AND w.semester = :semester " +
           "ORDER BY w.priority")
    List<WishlistItem> findByUserIdAndSemesterWithSubjectAndSchedules(@Param("userId") Long userId, @Param("semester") String semester);
    
    @Query("SELECT w FROM WishlistItem w JOIN FETCH w.subject s WHERE w.user.id = :userId AND s.id = :subjectId AND w.semester = :semester")
    Optional<WishlistItem> findByUserIdAndSubjectIdAndSemester(@Param("userId") Long userId, @Param("subjectId") Long subjectId, @Param("semester") String semester);
    
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM WishlistItem w WHERE w.user.id = :userId AND w.subject.id = :subjectId AND w.semester = :semester")
    int deleteByUserIdAndSubjectIdAndSemester(@Param("userId") Long userId, @Param("subjectId") Long subjectId, @Param("semester") String semester);
}