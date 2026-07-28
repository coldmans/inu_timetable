package inu.timetable.controller;

import inu.timetable.dto.SubjectDto;
import inu.timetable.dto.SubjectFilterCriteria;
import inu.timetable.enums.SubjectType;
import inu.timetable.exception.ApiException;
import inu.timetable.service.SubjectQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubjectControllerTest {

    @org.mockito.Mock
    private SubjectQueryService subjectQueryService;

    @Test
    void getAllSubjectsClampsPagingAndDelegatesToQueryService() {
        SubjectController controller = new SubjectController(subjectQueryService);
        Page<SubjectDto> expected = new PageImpl<>(List.of(), PageRequest.of(0, 100), 0);
        when(subjectQueryService.findActiveSubjects(0, 100)).thenReturn(expected);

        assertThat(controller.getAllSubjects(-1, 500)).isSameAs(expected);
        verify(subjectQueryService).findActiveSubjects(0, 100);
    }

    @Test
    void listingEndpointsDelegateToQueryService() {
        SubjectController controller = new SubjectController(subjectQueryService);
        List<SubjectDto> expected = List.of();
        when(subjectQueryService.findBySubjectType(SubjectType.전심)).thenReturn(expected);
        when(subjectQueryService.findByDepartment("컴퓨터공학부")).thenReturn(expected);
        when(subjectQueryService.findByGrade(2)).thenReturn(expected);
        when(subjectQueryService.findByProfessor("홍길동")).thenReturn(expected);

        assertThat(controller.getSubjectsByType(SubjectType.전심)).isSameAs(expected);
        assertThat(controller.getSubjectsByDepartment("컴퓨터공학부")).isSameAs(expected);
        assertThat(controller.getSubjectsByGrade(2)).isSameAs(expected);
        assertThat(controller.getSubjectsByProfessor("홍길동")).isSameAs(expected);
    }

    @Test
    void getAllDepartmentsDelegatesSemesterToQueryService() {
        SubjectController controller = new SubjectController(subjectQueryService);
        List<String> expected = List.of("경제학과(야)", "지능형로봇시스템연계전공");
        when(subjectQueryService.findDistinctDepartments("2026-2")).thenReturn(expected);

        assertThat(controller.getAllDepartments("2026-2")).isSameAs(expected);
        verify(subjectQueryService).findDistinctDepartments("2026-2");
    }

    @Test
    void filterSubjectsNormalizesRequestAndDelegatesToQueryService() {
        SubjectController controller = new SubjectController(subjectQueryService);
        SubjectFilterCriteria criteria = SubjectFilterCriteria.of(
                " 2026-1 ", " 자료구조 ", null, null, "전체",
                List.of("컴퓨터공학부, 정보통신공학과", "전체"),
                null, null, null, null, null, null, true, null, null, 0, 100);
        Page<SubjectDto> expected = new PageImpl<>(List.of(), PageRequest.of(0, 100), 0);
        when(subjectQueryService.filterSubjects(criteria)).thenReturn(expected);

        Page<SubjectDto> result = controller.filterSubjects(
                " 2026-1 ", " 자료구조 ", null, null, "전체",
                List.of("컴퓨터공학부, 정보통신공학과", "전체"),
                null, null, null, null, null, null, true, null, null, -1, 500);

        assertThat(result).isSameAs(expected);
        verify(subjectQueryService).filterSubjects(criteria);
    }

    @Test
    void filterSubjectsPassesTimeBlocksToQueryService() {
        SubjectController controller = new SubjectController(subjectQueryService);
        List<String> timeBlocks = List.of("금:4-9", "수:4-10");
        SubjectFilterCriteria criteria = SubjectFilterCriteria.of(
                null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, timeBlocks, 0, 20);
        Page<SubjectDto> expected = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(subjectQueryService.filterSubjects(criteria)).thenReturn(expected);

        Page<SubjectDto> result = controller.filterSubjects(
                null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, timeBlocks, 0, 20);

        assertThat(result).isSameAs(expected);
        // 요일 순서(월화수목금토)로 정렬되어 캐시 키가 안정화된다.
        verify(subjectQueryService).filterSubjects(criteria);
        assertThat(criteria.timeBlocks()).containsExactly("수:4-10", "금:4-9");
    }

    @Test
    void filterSubjectsRejectsMalformedTimeBlocks() {
        SubjectController controller = new SubjectController(subjectQueryService);

        assertThatThrownBy(() -> controller.filterSubjects(
                null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                List.of("월:abc"), 0, 20))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> controller.filterSubjects(
                null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                List.of("일:1-2"), 0, 20))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
