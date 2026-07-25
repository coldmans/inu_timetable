package inu.timetable.service;

import inu.timetable.config.CacheConfig;
import inu.timetable.dto.SubjectDto;
import inu.timetable.dto.SubjectFilterCriteria;
import inu.timetable.entity.Schedule;
import inu.timetable.entity.Subject;
import inu.timetable.enums.ClassMethod;
import inu.timetable.enums.SubjectType;
import inu.timetable.event.SubjectDataChangedEvent;
import inu.timetable.event.SubjectPopularityChangedEvent;
import inu.timetable.repository.SubjectRepository;
import inu.timetable.repository.UserTimetableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = {
        JacksonAutoConfiguration.class,
        CacheConfig.class,
        SubjectCacheEvictionService.class,
        SubjectFilterCacheService.class,
        SubjectSearchCacheService.class,
        SubjectQueryService.class
}, properties = {
        "subject.cache.maximum-size=100",
        "subject.cache.expire-after-write=10m"
})
class SubjectQueryServiceCacheTest {

    @Autowired
    private SubjectQueryService subjectQueryService;

    @Autowired
    private SubjectCacheEvictionService subjectCacheEvictionService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @MockitoBean
    private SubjectRepository subjectRepository;

    @MockitoBean
    private UserTimetableRepository userTimetableRepository;

    @BeforeEach
    void setUp() {
        reset(subjectRepository, userTimetableRepository);
        subjectCacheEvictionService.evictAllSubjectReadCaches();
    }

    @Test
    void searchSubjectsCachesSameCriteriaUntilSubjectDataChanges() {
        when(subjectRepository.findBySubjectNameContainingAndActiveTrue("자료"))
                .thenReturn(List.of(subject(1L, "자료구조")), List.of(subject(2L, "자료구조응용")));

        List<SubjectDto> first = subjectQueryService.searchBySubjectName(" 자료 ", null);
        List<SubjectDto> second = subjectQueryService.searchBySubjectName("자료", null);

        assertThat(first).extracting(SubjectDto::getSubjectName).containsExactly("자료구조");
        assertThat(second).extracting(SubjectDto::getSubjectName).containsExactly("자료구조");
        verify(subjectRepository, times(1)).findBySubjectNameContainingAndActiveTrue("자료");

        eventPublisher.publishEvent(new SubjectDataChangedEvent("test"));
        assertThat(subjectQueryService.searchBySubjectName("자료", null))
                .extracting(SubjectDto::getSubjectName)
                .containsExactly("자료구조응용");
        verify(subjectRepository, times(2)).findBySubjectNameContainingAndActiveTrue("자료");
    }

    @Test
    void countActiveSubjectsCachesUntilSubjectDataChanges() {
        when(subjectRepository.countByActiveTrue()).thenReturn(2894L, 3000L);

        assertThat(subjectQueryService.countActiveSubjects()).isEqualTo(2894L);
        assertThat(subjectQueryService.countActiveSubjects()).isEqualTo(2894L);
        verify(subjectRepository, times(1)).countByActiveTrue();

        eventPublisher.publishEvent(new SubjectDataChangedEvent("test"));

        assertThat(subjectQueryService.countActiveSubjects()).isEqualTo(3000L);
        verify(subjectRepository, times(2)).countByActiveTrue();
    }

    @Test
    void filterSubjectsCachesSameCriteriaUntilRelevantDataChanges() {
        Pageable pageable = PageRequest.of(0, 20);
        SubjectFilterCriteria criteria = SubjectFilterCriteria.of(
                "2026-1", "자료", null, null, "컴퓨터공학부", List.of(),
                null, null, null, SubjectType.전심, 2, null, null, null, null, 0, 20);
        when(subjectRepository.findIdsWithFilters(
                eq("2026-1"),
                nullable(String.class),
                nullable(String.class),
                nullable(String.class),
                nullable(String.class),
                anyList(),
                anyInt(),
                nullable(String.class),
                nullable(Double.class),
                nullable(Double.class),
                nullable(SubjectType.class),
                nullable(Integer.class),
                nullable(Boolean.class),
                nullable(Integer.class),
                nullable(Boolean.class),
                nullable(ClassMethod.class),
                anyBoolean(),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Double.class),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(1L), pageable, 1));
        when(subjectRepository.findWithSchedulesByIds(List.of(1L)))
                .thenReturn(List.of(subject(1L, "자료구조")));
        when(userTimetableRepository.countAddedUsersBySubjectIds(List.of(1L)))
                .thenReturn(List.of(count(1L, 7L)), List.of(count(1L, 9L)));

        Page<SubjectDto> first = subjectQueryService.filterSubjects(criteria);
        Page<SubjectDto> second = subjectQueryService.filterSubjects(SubjectFilterCriteria.of(
                "2026-1", "자료", null, null, "컴퓨터공학부", List.of(),
                null, null, null, SubjectType.전심, 2, null, null, null, null, 0, 20));

        assertThat(first.getContent()).extracting(SubjectDto::getTimetableAddCount).containsExactly(7L);
        assertThat(second.getContent()).extracting(SubjectDto::getTimetableAddCount).containsExactly(7L);
        verify(subjectRepository, times(1)).findIdsWithFilters(
                eq("2026-1"),
                nullable(String.class),
                nullable(String.class),
                nullable(String.class),
                nullable(String.class),
                anyList(),
                anyInt(),
                nullable(String.class),
                nullable(Double.class),
                nullable(Double.class),
                nullable(SubjectType.class),
                nullable(Integer.class),
                nullable(Boolean.class),
                nullable(Integer.class),
                nullable(Boolean.class),
                nullable(ClassMethod.class),
                anyBoolean(),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Double.class),
                any(Pageable.class));
        verify(subjectRepository, times(1)).findWithSchedulesByIds(List.of(1L));
        verify(userTimetableRepository, times(1)).countAddedUsersBySubjectIds(List.of(1L));

        eventPublisher.publishEvent(new SubjectPopularityChangedEvent("test"));
        Page<SubjectDto> refreshed = subjectQueryService.filterSubjects(criteria);

        assertThat(refreshed.getContent()).extracting(SubjectDto::getTimetableAddCount).containsExactly(9L);
        verify(subjectRepository, times(2)).findIdsWithFilters(
                eq("2026-1"),
                nullable(String.class),
                nullable(String.class),
                nullable(String.class),
                nullable(String.class),
                anyList(),
                anyInt(),
                nullable(String.class),
                nullable(Double.class),
                nullable(Double.class),
                nullable(SubjectType.class),
                nullable(Integer.class),
                nullable(Boolean.class),
                nullable(Integer.class),
                nullable(Boolean.class),
                nullable(ClassMethod.class),
                anyBoolean(),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Double.class),
                any(Pageable.class));
        verify(subjectRepository, times(2)).findWithSchedulesByIds(List.of(1L));
        verify(userTimetableRepository, times(2)).countAddedUsersBySubjectIds(List.of(1L));
    }

    @Test
    void filterSubjectsClampsAndCachesOversizedPages() {
        Pageable pageable = PageRequest.of(0, SubjectFilterCacheService.MAX_CACHEABLE_PAGE_SIZE);
        SubjectFilterCriteria criteria = SubjectFilterCriteria.of(
                null, null, null, null, null, List.of(),
                null, null, null, null, null, null, null, null, null, 0, 101);
        when(subjectRepository.findIdsWithFilters(
                nullable(String.class),
                nullable(String.class),
                nullable(String.class),
                nullable(String.class),
                nullable(String.class),
                anyList(),
                anyInt(),
                nullable(String.class),
                nullable(Double.class),
                nullable(Double.class),
                nullable(SubjectType.class),
                nullable(Integer.class),
                nullable(Boolean.class),
                nullable(Integer.class),
                nullable(Boolean.class),
                nullable(ClassMethod.class),
                anyBoolean(),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Double.class),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        subjectQueryService.filterSubjects(criteria);
        subjectQueryService.filterSubjects(criteria);

        assertThat(criteria.size()).isEqualTo(SubjectFilterCacheService.MAX_CACHEABLE_PAGE_SIZE);
        verify(subjectRepository, times(1)).findIdsWithFilters(
                nullable(String.class),
                nullable(String.class),
                nullable(String.class),
                nullable(String.class),
                nullable(String.class),
                anyList(),
                anyInt(),
                nullable(String.class),
                nullable(Double.class),
                nullable(Double.class),
                nullable(SubjectType.class),
                nullable(Integer.class),
                nullable(Boolean.class),
                nullable(Integer.class),
                nullable(Boolean.class),
                nullable(ClassMethod.class),
                anyBoolean(),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Double.class),
                any(Pageable.class));
    }

    private UserTimetableRepository.SubjectTimetableAddCount count(Long subjectId, Long timetableAddCount) {
        return new UserTimetableRepository.SubjectTimetableAddCount() {
            @Override
            public Long getSubjectId() {
                return subjectId;
            }

            @Override
            public Long getTimetableAddCount() {
                return timetableAddCount;
            }
        };
    }

    private Subject subject(Long id, String subjectName) {
        Subject subject = Subject.builder()
                .id(id)
                .subjectName(subjectName)
                .credits(3)
                .professor("김교수")
                .department("컴퓨터공학부")
                .grade(2)
                .subjectType(SubjectType.전심)
                .classMethod(ClassMethod.OFFLINE)
                .isNight(false)
                .schedules(new ArrayList<>())
                .build();
        subject.getSchedules().add(Schedule.builder()
                .id(10L)
                .subject(subject)
                .dayOfWeek("월")
                .startTime(1.0)
                .endTime(2.5)
                .build());
        return subject;
    }
}
