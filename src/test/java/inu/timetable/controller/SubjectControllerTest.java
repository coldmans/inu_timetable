package inu.timetable.controller;

import inu.timetable.dto.SubjectDto;
import inu.timetable.dto.SubjectFilterCriteria;
import inu.timetable.repository.SubjectRepository;
import inu.timetable.service.SubjectQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubjectControllerTest {

    @org.mockito.Mock
    private SubjectRepository subjectRepository;

    @org.mockito.Mock
    private SubjectQueryService subjectQueryService;

    @Test
    void filterSubjectsNormalizesRequestAndDelegatesToQueryService() {
        SubjectController controller = new SubjectController(subjectRepository, subjectQueryService);
        SubjectFilterCriteria criteria = SubjectFilterCriteria.of(
                " 2026-1 ", " 자료구조 ", null, "전체",
                List.of("컴퓨터공학부, 정보통신공학과", "전체"),
                null, null, null, null, null, null, true, null, 0, 100);
        Page<SubjectDto> expected = new PageImpl<>(List.of(), PageRequest.of(0, 100), 0);
        when(subjectQueryService.filterSubjects(criteria)).thenReturn(expected);

        Page<SubjectDto> result = controller.filterSubjects(
                " 2026-1 ", " 자료구조 ", null, "전체",
                List.of("컴퓨터공학부, 정보통신공학과", "전체"),
                null, null, null, null, null, null, true, null, -1, 500);

        assertThat(result).isSameAs(expected);
        verify(subjectQueryService).filterSubjects(criteria);
    }
}
