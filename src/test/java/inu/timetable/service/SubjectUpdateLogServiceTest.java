package inu.timetable.service;

import inu.timetable.entity.SubjectUpdateLog;
import inu.timetable.repository.SubjectUpdateLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubjectUpdateLogServiceTest {

    @Mock
    private SubjectUpdateLogRepository subjectUpdateLogRepository;

    @Test
    void recordsAppliedAtAsUtcInstantRegardlessOfServerTimezone() {
        Clock utcClock = Clock.fixed(
                Instant.parse("2026-07-30T12:00:00Z"),
                ZoneOffset.UTC);
        SubjectUpdateLogService service =
                new SubjectUpdateLogService(subjectUpdateLogRepository, utcClock);
        when(subjectUpdateLogRepository.save(any(SubjectUpdateLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SubjectUpdateLog saved =
                service.record("2026-2", "SYLLABUS_JSON_REVIEW", 1, 2, 3);

        assertThat(saved.getAppliedAt())
                .isEqualTo(Instant.parse("2026-07-30T12:00:00Z"));
    }
}
