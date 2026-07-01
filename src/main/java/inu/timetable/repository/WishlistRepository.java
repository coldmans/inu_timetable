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

    interface SubjectWishlistCount {
        Long getSubjectId();
        Long getWishlistCount();
    }
    
    List<WishlistItem> findByUserIdAndSemester(Long userId, String semester);
    
    @Query("SELECT DISTINCT w FROM WishlistItem w " +
           "JOIN FETCH w.subject s " +
           "LEFT JOIN FETCH s.schedules " +
           "WHERE w.user.id = :userId AND w.semester = :semester " +
           "ORDER BY w.priority")
    List<WishlistItem> findByUserIdAndSemesterWithSubjectAndSchedules(@Param("userId") Long userId, @Param("semester") String semester);
    
    @Query("SELECT w FROM WishlistItem w JOIN FETCH w.subject s WHERE w.user.id = :userId AND s.id = :subjectId")
    List<WishlistItem> findAllByUserIdAndSubjectId(@Param("userId") Long userId, @Param("subjectId") Long subjectId);

    // 학기까지 포함한 중복 검사(semester null 도 정확히 구분). 다른 학기에 같은 과목을 담을 수 있게 한다.
    @Query("SELECT COUNT(w) > 0 FROM WishlistItem w " +
           "WHERE w.user.id = :userId AND w.subject.id = :subjectId " +
           "AND ((:semester IS NULL AND w.semester IS NULL) OR w.semester = :semester)")
    boolean existsByUserIdAndSubjectIdAndSemester(@Param("userId") Long userId,
                                                  @Param("subjectId") Long subjectId,
                                                  @Param("semester") String semester);
    
    @Modifying
    @Query("DELETE FROM WishlistItem w WHERE w.user.id = :userId AND w.subject.id = :subjectId")
    void deleteByUserIdAndSubjectId(@Param("userId") Long userId, @Param("subjectId") Long subjectId);

    @Query("SELECT w.subject.id AS subjectId, COUNT(w.id) AS wishlistCount " +
           "FROM WishlistItem w " +
           "WHERE w.subject.id IN :subjectIds " +
           "GROUP BY w.subject.id")
    List<SubjectWishlistCount> countBySubjectIds(@Param("subjectIds") List<Long> subjectIds);
}
