package inu.timetable.repository;

import inu.timetable.entity.SubjectUpdateLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubjectUpdateLogRepository extends JpaRepository<SubjectUpdateLog, Long> {

    List<SubjectUpdateLog> findAllByOrderByAppliedAtDescIdDesc(Pageable pageable);
}
