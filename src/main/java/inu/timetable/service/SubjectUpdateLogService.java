package inu.timetable.service;

import inu.timetable.dto.SubjectUpdateLogResponse;
import inu.timetable.entity.SubjectUpdateLog;
import inu.timetable.repository.SubjectUpdateLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SubjectUpdateLogService {

    static final int MAX_LIMIT = 50;

    private final SubjectUpdateLogRepository subjectUpdateLogRepository;
    private final Clock clock;

    @Transactional
    public SubjectUpdateLog record(
            String semester, String sourceFormat, int addedCount, int modifiedCount, int removedCount) {
        return subjectUpdateLogRepository.save(SubjectUpdateLog.builder()
                .appliedAt(LocalDateTime.now(clock))
                .semester(semester)
                .sourceFormat(sourceFormat)
                .addedCount(addedCount)
                .modifiedCount(modifiedCount)
                .removedCount(removedCount)
                .build());
    }

    @Transactional(readOnly = true)
    public List<SubjectUpdateLogResponse> getRecentLogs(int limit) {
        int clampedLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
        return subjectUpdateLogRepository
                .findAllByOrderByAppliedAtDescIdDesc(PageRequest.of(0, clampedLimit))
                .stream()
                .map(SubjectUpdateLogResponse::fromEntity)
                .toList();
    }
}
