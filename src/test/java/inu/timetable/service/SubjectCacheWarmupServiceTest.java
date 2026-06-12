package inu.timetable.service;

import inu.timetable.dto.SubjectFilterCriteria;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SubjectCacheWarmupServiceTest {

    @Test
    void warmUpSubmitsCommonAndGradeFilterQueries() {
        SubjectQueryService subjectQueryService = mock(SubjectQueryService.class);
        when(subjectQueryService.findDistinctGrades()).thenReturn(List.of(1, 2));
        SubjectCacheWarmupService warmupService = new SubjectCacheWarmupService(
                subjectQueryService, true, 2, 20);

        SubjectCacheWarmupService.WarmupResult result = warmupService.warmUp();

        assertThat(result.skipped()).isFalse();
        assertThat(result.submitted()).isEqualTo(5);
        assertThat(result.succeeded()).isEqualTo(5);
        assertThat(result.failed()).isZero();
        verify(subjectQueryService).findDistinctGrades();
        verify(subjectQueryService).countActiveSubjects();
        verify(subjectQueryService).findDistinctDepartments();
        verify(subjectQueryService).filterSubjects(SubjectFilterCriteria.of(
                null, null, null, null,
                null, null, null, null, null, null, 0, 20));
        verify(subjectQueryService).filterSubjects(SubjectFilterCriteria.of(
                null, null, null, null,
                null, null, null, 1, null, null, 0, 20));
        verify(subjectQueryService).filterSubjects(SubjectFilterCriteria.of(
                null, null, null, null,
                null, null, null, 2, null, null, 0, 20));
    }

    @Test
    void disabledWarmUpDoesNothing() {
        SubjectQueryService subjectQueryService = mock(SubjectQueryService.class);
        SubjectCacheWarmupService warmupService = new SubjectCacheWarmupService(
                subjectQueryService, false, 2, 20);

        SubjectCacheWarmupService.WarmupResult result = warmupService.warmUp();

        assertThat(result.skipped()).isTrue();
        verifyNoInteractions(subjectQueryService);
    }

    @Test
    void warmUpCapsConfiguredPageSizeToCacheableLimit() {
        SubjectQueryService subjectQueryService = mock(SubjectQueryService.class);
        when(subjectQueryService.findDistinctGrades()).thenReturn(List.of());
        SubjectCacheWarmupService warmupService = new SubjectCacheWarmupService(
                subjectQueryService, true, 0, 200);

        SubjectCacheWarmupService.WarmupResult result = warmupService.warmUp();

        assertThat(result.succeeded()).isEqualTo(3);
        verify(subjectQueryService).filterSubjects(SubjectFilterCriteria.of(
                null, null, null, null,
                null, null, null, null, null, null, 0,
                SubjectFilterCacheService.MAX_CACHEABLE_PAGE_SIZE));
    }
}
