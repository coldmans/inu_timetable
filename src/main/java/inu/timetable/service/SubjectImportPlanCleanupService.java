package inu.timetable.service;

import inu.timetable.repository.SubjectImportPlanRepository;
import inu.timetable.util.BusinessTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@Slf4j
public class SubjectImportPlanCleanupService {

    private final SubjectImportPlanRepository planRepository;
    private final Clock clock;
    private final int retentionDays;

    public SubjectImportPlanCleanupService(
            SubjectImportPlanRepository planRepository,
            Clock clock,
            @Value("${subject.import.plan-retention-days:90}") int retentionDays) {
        this.planRepository = planRepository;
        this.clock = clock;
        this.retentionDays = Math.max(1, retentionDays);
    }

    @Transactional
    @Scheduled(
            cron = "${subject.import.cleanup-cron:0 35 3 * * *}",
            zone = "Asia/Seoul")
    public int cleanupExpired() {
        LocalDateTime cutoff =
                BusinessTime.nowInKorea(clock).minusDays(retentionDays);
        int deleted = planRepository.deleteAllCreatedBefore(cutoff);
        if (deleted > 0) {
            log.info(
                    "과목 검토 이력 보존 기간이 지난 항목을 정리했습니다. "
                            + "retentionDays={}, deleted={}",
                    retentionDays,
                    deleted);
        }
        return deleted;
    }
}
