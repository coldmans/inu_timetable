package inu.timetable.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import inu.timetable.dto.SubjectImportPlanResponse;
import inu.timetable.entity.Schedule;
import inu.timetable.entity.ScheduleRoomSegment;
import inu.timetable.entity.Subject;
import inu.timetable.entity.SubjectImportPlan;
import inu.timetable.enums.ClassMethod;
import inu.timetable.enums.SubjectImportChangeCategory;
import inu.timetable.enums.SubjectType;
import inu.timetable.repository.SubjectImportPlanRepository;
import inu.timetable.repository.SubjectRepository;
import inu.timetable.repository.UserTimetableRepository;
import inu.timetable.repository.WishlistRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubjectImportReviewServiceTest {

    @Mock OfficialSubjectImportService officialSubjectImportService;
    @Mock SubjectRepository subjectRepository;
    @Mock SubjectImportPlanRepository planRepository;
    @Mock UserTimetableRepository userTimetableRepository;
    @Mock WishlistRepository wishlistRepository;
    @Mock TimetableConflictResolutionService timetableConflictResolutionService;
    @Mock SubjectUpdateLogService subjectUpdateLogService;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock EntityManager entityManager;

    private SubjectImportReviewService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new SubjectImportReviewService(
                officialSubjectImportService,
                subjectRepository,
                planRepository,
                userTimetableRepository,
                wishlistRepository,
                timetableConflictResolutionService,
                subjectUpdateLogService,
                eventPublisher,
                objectMapper,
                entityManager,
                Clock.fixed(Instant.parse("2026-07-30T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void previewCategorizesBeforeAfterAndSplitsUserImpact() throws Exception {
        Subject changed = subject(1L, "AI001", "기존과목", true, "월", 1.0, 3.0, "27-101");
        Subject canceled = subject(2L, "OLD001", "폐강예정", true, "화", 1.0, 3.0, "27-201");
        OfficialSubjectImportService.OfficialSubjectRecord changedRecord = record(
                "AI001", "변경과목", true, "월", 2.0, 4.0, "27-102");
        OfficialSubjectImportService.OfficialSubjectRecord addedRecord = record(
                "NEW001", "새과목", false, "수", 1.0, 2.0, "27-301");
        MockMultipartFile file = new MockMultipartFile(
                "file", "inu.json", "application/json", "{}".getBytes());

        when(officialSubjectImportService.parseOfficialFile(file, "2026-2"))
                .thenReturn(new OfficialSubjectImportService.ParsedWorkbook(
                        List.of(changedRecord, addedRecord),
                        "SYLLABUS_JSON"));
        when(subjectRepository.findImportCandidatesBySemester("2026-2"))
                .thenReturn(List.of(changed, canceled));
        when(planRepository.save(any(SubjectImportPlan.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userTimetableRepository.countAddedUsersBySubjectIdsAndSemester(anyList(), eq("2026-2")))
                .thenReturn(List.of());
        when(wishlistRepository.countBySubjectIdsAndSemester(anyList(), eq("2026-2")))
                .thenReturn(List.of());
        when(userTimetableRepository.findDistinctUserIdsBySubjectIdsAndSemester(anyList(), eq("2026-2")))
                .thenReturn(List.of(11L, 12L));
        when(wishlistRepository.findDistinctUserIdsBySubjectIdsAndSemester(anyList(), eq("2026-2")))
                .thenReturn(List.of(12L, 13L));
        when(userTimetableRepository.findAllBySubjectIdsAndSemesterWithUserAndSubject(anyList(), eq("2026-2")))
                .thenReturn(List.of());

        SubjectImportPlanResponse response = service.preview(file, "2026-2", true);

        assertThat(response.createdAt()).isEqualTo(LocalDateTime.parse("2026-07-30T21:00:00"));
        assertThat(response.changedCount()).isEqualTo(3);
        assertThat(response.unchangedCount()).isZero();
        assertThat(response.impact().timetableUsers()).isEqualTo(2);
        assertThat(response.impact().wishlistUsers()).isEqualTo(2);
        assertThat(response.impact().totalUsers()).isEqualTo(3);
        assertThat(response.changes())
                .filteredOn(change -> change.courseCode().equals("AI001"))
                .singleElement()
                .satisfies(change -> {
                    assertThat(change.categories()).contains(
                            SubjectImportChangeCategory.TIME_CHANGED,
                            SubjectImportChangeCategory.ROOM_CHANGED,
                            SubjectImportChangeCategory.SYLLABUS_ATTACHED,
                            SubjectImportChangeCategory.DETAILS_CHANGED);
                    assertThat(change.fields())
                            .extracting(SubjectImportPlanResponse.FieldChange::field)
                            .contains("교과목명", "시간표", "강의실", "강의계획서 첨부");
                });
        assertThat(response.changes())
                .filteredOn(change -> change.courseCode().equals("OLD001"))
                .singleElement()
                .extracting(SubjectImportPlanResponse.ChangeItem::categories)
                .asString()
                .contains("CANCELED");
    }

    @Test
    void previewDoesNotClassifyRoomChangeWhenOnlyTimeMoves() throws Exception {
        Subject existing = subject(1L, "AI001", "기존과목", true, "월", 1.0, 3.0, "27-101");
        OfficialSubjectImportService.OfficialSubjectRecord incoming = record(
                "AI001", "기존과목", false, "월", 2.0, 4.0, "27-101");

        SubjectImportPlanResponse.ChangeItem change = previewSingle(existing, incoming);

        assertThat(change.categories())
                .contains(SubjectImportChangeCategory.TIME_CHANGED)
                .doesNotContain(SubjectImportChangeCategory.ROOM_CHANGED);
        assertThat(change.fields())
                .extracting(SubjectImportPlanResponse.FieldChange::field)
                .contains("시간표")
                .doesNotContain("강의실");
    }

    @Test
    void previewClassifiesRoomChangeWhenOnlyPhysicalRoomMoves() throws Exception {
        Subject existing = subject(1L, "AI001", "기존과목", true, "월", 1.0, 3.0, "27-101");
        OfficialSubjectImportService.OfficialSubjectRecord incoming = record(
                "AI001", "기존과목", false, "월", 1.0, 3.0, "27-201");

        SubjectImportPlanResponse.ChangeItem change = previewSingle(existing, incoming);

        assertThat(change.categories())
                .contains(SubjectImportChangeCategory.ROOM_CHANGED)
                .doesNotContain(SubjectImportChangeCategory.TIME_CHANGED);
        assertThat(change.fields())
                .extracting(SubjectImportPlanResponse.FieldChange::field)
                .contains("강의실")
                .doesNotContain("시간표");
    }

    @Test
    void previewBatchLoadsCandidateUserTimetablesForConflictProjection() throws Exception {
        Subject existing = subject(1L, "AI001", "기존과목", true, "월", 1.0, 3.0, "27-101");
        OfficialSubjectImportService.OfficialSubjectRecord incoming = record(
                "AI001", "기존과목", false, "월", 2.0, 4.0, "27-101");
        MockMultipartFile file = new MockMultipartFile(
                "file", "inu.json", "application/json", "{}".getBytes());

        when(officialSubjectImportService.parseOfficialFile(file, "2026-2"))
                .thenReturn(new OfficialSubjectImportService.ParsedWorkbook(
                        List.of(incoming),
                        "SYLLABUS_JSON"));
        when(subjectRepository.findImportCandidatesBySemester("2026-2"))
                .thenReturn(List.of(existing));
        when(planRepository.save(any(SubjectImportPlan.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userTimetableRepository.findDistinctUserIdsBySubjectIdsAndSemester(
                anyList(), eq("2026-2")))
                .thenReturn(List.of(11L, 12L));
        when(userTimetableRepository.findAllByUserIdsAndSemesterWithSubjectAndSchedules(
                List.of(11L, 12L), "2026-2"))
                .thenReturn(List.of());

        service.preview(file, "2026-2", false);

        verify(userTimetableRepository)
                .findAllByUserIdsAndSemesterWithSubjectAndSchedules(
                        List.of(11L, 12L), "2026-2");
        verify(userTimetableRepository, never())
                .findByUserIdAndSemesterWithSubjectAndSchedules(any(), eq("2026-2"));
    }

    @Test
    void previewRejectsDuplicateExistingCourseCodes() throws Exception {
        Subject first = subject(1L, "AI001", "첫번째", true, "월", 1.0, 3.0, "27-101");
        Subject second = subject(2L, "AI001", "두번째", true, "화", 1.0, 3.0, "27-201");
        OfficialSubjectImportService.OfficialSubjectRecord incoming = record(
                "AI001", "변경과목", false, "월", 1.0, 3.0, "27-101");
        MockMultipartFile file = new MockMultipartFile(
                "file", "inu.json", "application/json", "{}".getBytes());

        when(officialSubjectImportService.parseOfficialFile(file, "2026-2"))
                .thenReturn(new OfficialSubjectImportService.ParsedWorkbook(
                        List.of(incoming),
                        "SYLLABUS_JSON"));
        when(subjectRepository.findImportCandidatesBySemester("2026-2"))
                .thenReturn(List.of(first, second));

        assertThatThrownBy(() -> service.preview(file, "2026-2", false))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("중복")
                .hasMessageContaining("AI001");
    }

    @Test
    void applyRejectsPlanWhenDatabaseChangedAfterPreview() throws Exception {
        Subject original = subject(1L, "AI001", "기존과목", true, "월", 1.0, 3.0, "27-101");
        OfficialSubjectImportService.OfficialSubjectRecord record = record(
                "AI001", "변경과목", true, "월", 2.0, 4.0, "27-102");
        MockMultipartFile file = new MockMultipartFile(
                "file", "inu.json", "application/json", "{}".getBytes());
        ArgumentCaptor<SubjectImportPlan> planCaptor = ArgumentCaptor.forClass(SubjectImportPlan.class);

        when(officialSubjectImportService.parseOfficialFile(file, "2026-2"))
                .thenReturn(new OfficialSubjectImportService.ParsedWorkbook(List.of(record), "SYLLABUS_JSON"));
        when(subjectRepository.findImportCandidatesBySemester("2026-2"))
                .thenReturn(List.of(original));
        when(planRepository.save(planCaptor.capture()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userTimetableRepository.countAddedUsersBySubjectIdsAndSemester(anyList(), eq("2026-2")))
                .thenReturn(List.of());
        when(wishlistRepository.countBySubjectIdsAndSemester(anyList(), eq("2026-2")))
                .thenReturn(List.of());
        when(userTimetableRepository.findDistinctUserIdsBySubjectIdsAndSemester(anyList(), eq("2026-2")))
                .thenReturn(List.of());
        when(wishlistRepository.findDistinctUserIdsBySubjectIdsAndSemester(anyList(), eq("2026-2")))
                .thenReturn(List.of());

        SubjectImportPlanResponse preview = service.preview(file, "2026-2", true);
        when(planRepository.findByIdForUpdate(preview.planId()))
                .thenReturn(java.util.Optional.of(planCaptor.getValue()));
        original.setProfessor("미리보기 이후 바뀐 교수");

        assertThatThrownBy(() -> service.apply(preview.planId(), List.of("AI001")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("다시 검토");
    }

    @Test
    void applyRejectsAlreadyAppliedPlan() {
        SubjectImportPlan appliedPlan = SubjectImportPlan.builder()
                .id("applied-plan")
                .status("APPLIED")
                .build();
        when(planRepository.findByIdForUpdate("applied-plan"))
                .thenReturn(java.util.Optional.of(appliedPlan));

        assertThatThrownBy(() -> service.apply("applied-plan", List.of("AI001")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("이미 반영");
    }

    private SubjectImportPlanResponse.ChangeItem previewSingle(
            Subject existing,
            OfficialSubjectImportService.OfficialSubjectRecord incoming) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "inu.json", "application/json", "{}".getBytes());
        when(officialSubjectImportService.parseOfficialFile(file, "2026-2"))
                .thenReturn(new OfficialSubjectImportService.ParsedWorkbook(
                        List.of(incoming),
                        "SYLLABUS_JSON"));
        when(subjectRepository.findImportCandidatesBySemester("2026-2"))
                .thenReturn(List.of(existing));
        when(planRepository.save(any(SubjectImportPlan.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        return service.preview(file, "2026-2", false).changes().get(0);
    }

    private Subject subject(
            Long id,
            String code,
            String name,
            boolean active,
            String day,
            double start,
            double end,
            String room) {
        Subject subject = Subject.builder()
                .id(id)
                .courseCode(code)
                .semester("2026-2")
                .active(active)
                .subjectName(name)
                .credits(3)
                .professor("교수")
                .department("임베디드시스템공학과")
                .grade(2)
                .subjectType(SubjectType.전심)
                .classMethod(ClassMethod.OFFLINE)
                .isNight(false)
                .syllabusAvailable(false)
                .schedules(new ArrayList<>())
                .build();
        Schedule schedule = Schedule.builder()
                .subject(subject)
                .dayOfWeek(day)
                .startTime(start)
                .endTime(end)
                .roomSegments(new LinkedHashSet<>())
                .build();
        schedule.getRoomSegments().add(ScheduleRoomSegment.builder()
                .schedule(schedule)
                .room(room)
                .startTime(start)
                .endTime(end)
                .build());
        subject.getSchedules().add(schedule);
        return subject;
    }

    private OfficialSubjectImportService.OfficialSubjectRecord record(
            String code,
            String name,
            boolean syllabus,
            String day,
            double start,
            double end,
            String room) {
        return new OfficialSubjectImportService.OfficialSubjectRecord(
                code,
                "2026-2",
                name,
                3,
                "교수",
                "임베디드시스템공학과",
                2,
                SubjectType.전심,
                ClassMethod.OFFLINE,
                false,
                syllabus,
                List.of(new OfficialSubjectImportService.ScheduleValue(
                        day,
                        start,
                        end,
                        List.of(new OfficialSubjectImportService.RoomSegmentValue(
                                room,
                                day,
                                start,
                                end)))));
    }
}
