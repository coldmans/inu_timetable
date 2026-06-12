package inu.timetable.service;

import inu.timetable.dto.SubjectManagementRequest;
import inu.timetable.dto.SubjectManagementResponse;
import inu.timetable.entity.Schedule;
import inu.timetable.entity.Subject;
import inu.timetable.enums.ClassMethod;
import inu.timetable.enums.SubjectType;
import inu.timetable.event.SubjectDataChangedEvent;
import inu.timetable.repository.SubjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubjectAdminServiceTest {

    @Mock
    private SubjectRepository subjectRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private SubjectAdminService subjectAdminService;

    @BeforeEach
    void setUp() {
        subjectAdminService = new SubjectAdminService(subjectRepository, eventPublisher);
    }

    @Test
    void createSubjectSavesSubjectWithSchedules() {
        when(subjectRepository.save(any(Subject.class))).thenAnswer(invocation -> {
            Subject subject = invocation.getArgument(0);
            subject.setId(1L);
            return subject;
        });

        SubjectManagementResponse response = subjectAdminService.createSubject(sampleRequest());

        ArgumentCaptor<Subject> captor = ArgumentCaptor.forClass(Subject.class);
        verify(subjectRepository).save(captor.capture());
        Subject savedSubject = captor.getValue();

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(savedSubject.getSubjectName()).isEqualTo("자료구조");
        assertThat(savedSubject.getSubjectType()).isEqualTo(SubjectType.전심);
        assertThat(savedSubject.getSchedules()).hasSize(1);
        assertThat(savedSubject.getSchedules().get(0).getSubject()).isSameAs(savedSubject);
        verify(eventPublisher).publishEvent(any(SubjectDataChangedEvent.class));
    }

    @Test
    void updateSubjectReplacesExistingSchedules() {
        Subject subject = sampleSubject();
        when(subjectRepository.findWithSchedulesById(1L)).thenReturn(Optional.of(subject));

        SubjectManagementRequest request = sampleRequest();
        request.setSubjectName("운영체제");
        request.setSchedules(List.of(
                new SubjectManagementRequest.ScheduleRequest("화", 3.0, 4.5),
                new SubjectManagementRequest.ScheduleRequest("목", 7.0, 8.0)));

        SubjectManagementResponse response = subjectAdminService.updateSubject(1L, request);

        assertThat(response.getSubjectName()).isEqualTo("운영체제");
        assertThat(response.getSchedules()).hasSize(2);
        assertThat(subject.getSchedules()).extracting(Schedule::getDayOfWeek)
                .containsExactly("화", "목");
        assertThat(subject.getSchedules()).allSatisfy(schedule -> assertThat(schedule.getSubject()).isSameAs(subject));
        verify(eventPublisher).publishEvent(any(SubjectDataChangedEvent.class));
    }

    @Test
    void updateSubjectRejectsInvalidScheduleRange() {
        Subject subject = sampleSubject();
        when(subjectRepository.findWithSchedulesById(1L)).thenReturn(Optional.of(subject));

        SubjectManagementRequest request = sampleRequest();
        request.setSchedules(List.of(new SubjectManagementRequest.ScheduleRequest("월", 4.0, 4.0)));

        assertThatThrownBy(() -> subjectAdminService.updateSubject(1L, request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void deleteSubjectReturnsConflictWhenSubjectIsReferenced() {
        Subject subject = sampleSubject();
        when(subjectRepository.findWithSchedulesById(1L)).thenReturn(Optional.of(subject));
        doThrow(new DataIntegrityViolationException("fk")).when(subjectRepository).flush();

        assertThatThrownBy(() -> subjectAdminService.deleteSubject(1L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));

        verify(subjectRepository).delete(subject);
        verify(eventPublisher, never()).publishEvent(any(SubjectDataChangedEvent.class));
    }

    private SubjectManagementRequest sampleRequest() {
        return new SubjectManagementRequest(
                "CSE0001001",
                "2026-1",
                true,
                "자료구조",
                3,
                "김교수",
                "컴퓨터공학부",
                2,
                SubjectType.전심,
                ClassMethod.OFFLINE,
                false,
                List.of(new SubjectManagementRequest.ScheduleRequest("월", 1.0, 2.5)));
    }

    private Subject sampleSubject() {
        Subject subject = Subject.builder()
                .id(1L)
                .subjectName("기존과목")
                .credits(3)
                .professor("이교수")
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
                .endTime(2.0)
                .build());
        return subject;
    }
}
