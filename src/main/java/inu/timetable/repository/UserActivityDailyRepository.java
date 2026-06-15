package inu.timetable.repository;

import inu.timetable.entity.UserActivityDaily;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface UserActivityDailyRepository extends JpaRepository<UserActivityDaily, Long> {

    boolean existsByUserIdAndActivityDate(Long userId, LocalDate activityDate);

    @Query("SELECT COUNT(DISTINCT activity.user.id) " +
           "FROM UserActivityDaily activity " +
           "WHERE activity.activityDate BETWEEN :startDate AND :endDate")
    long countDistinctUsersBetween(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
