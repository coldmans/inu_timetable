package inu.timetable.repository;

import inu.timetable.entity.UserNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UserNotificationRepository extends JpaRepository<UserNotification, Long> {

    List<UserNotification> findAllByUserIdAndReadAtIsNullOrderByCreatedAtAscIdAsc(Long userId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserNotification n SET n.readAt = :readAt " +
           "WHERE n.userId = :userId AND n.readAt IS NULL")
    int markAllRead(@Param("userId") Long userId, @Param("readAt") LocalDateTime readAt);
}
