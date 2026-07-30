package inu.timetable.repository;

import inu.timetable.entity.SubjectImportPlan;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SubjectImportPlanRepository extends JpaRepository<SubjectImportPlan, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT plan FROM SubjectImportPlan plan WHERE plan.id = :planId")
    Optional<SubjectImportPlan> findByIdForUpdate(@Param("planId") String planId);
}
