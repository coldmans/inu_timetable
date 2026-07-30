package inu.timetable.service;

import inu.timetable.repository.SubjectImportPlanRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubjectImportPlanCleanupServiceTest {

    @Mock
    private SubjectImportPlanRepository planRepository;

    @Test
    void deletesPlansOlderThanConfiguredRetentionPeriodInKoreanTime() {
        Clock utcClock = Clock.fixed(
                Instant.parse("2026-07-30T12:00:00Z"),
                ZoneOffset.UTC);
        SubjectImportPlanCleanupService service =
                new SubjectImportPlanCleanupService(planRepository, utcClock, 90);
        LocalDateTime cutoff = LocalDateTime.parse("2026-05-01T21:00:00");
        when(planRepository.deleteAllCreatedBefore(cutoff)).thenReturn(3);

        int deleted = service.cleanupExpired();

        assertThat(deleted).isEqualTo(3);
        verify(planRepository).deleteAllCreatedBefore(cutoff);
    }
}
