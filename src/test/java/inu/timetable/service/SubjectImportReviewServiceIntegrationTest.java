package inu.timetable.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import inu.timetable.dto.SubjectImportApplyResponse;
import inu.timetable.dto.SubjectImportPlanResponse;
import inu.timetable.dto.TimetableReconciliationResult;
import inu.timetable.entity.Subject;
import inu.timetable.enums.ClassMethod;
import inu.timetable.enums.SubjectType;
import inu.timetable.event.SubjectDataChangedEvent;
import inu.timetable.repository.SubjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:subject_import_review;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@Import({
        OfficialSubjectImportService.class,
        SubjectImportReviewService.class,
        SubjectImportReviewServiceIntegrationTest.Config.class
})
class SubjectImportReviewServiceIntegrationTest {

    static class Config {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-07-30T12:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired SubjectImportReviewService service;
    @Autowired SubjectRepository subjectRepository;

    @MockBean TimetableConflictResolutionService timetableConflictResolutionService;
    @MockBean SubjectUpdateLogService subjectUpdateLogService;
    @MockBean ApplicationEventPublisher eventPublisher;

    @Test
    void appliesOnlySelectedCourseAndVerifiesPersistedValues() throws Exception {
        subjectRepository.save(subject("AI001", "기존과목"));
        when(timetableConflictResolutionService.reconcileImportChanges(
                anyList(), anyList(), anyList(), anyString()))
                .thenReturn(new TimetableReconciliationResult(
                        0, 0, 0, 0, 0, 0, List.of()));

        MockMultipartFile json = new MockMultipartFile(
                "file",
                "INU_강의계획서_2026_20.json",
                "application/json",
                """
                {
                  "rows": [
                    {
                      "yy": "2026", "tmGbn": "20", "haksuNo": "AI001",
                      "scNm": "변경과목", "hp": {"hi": 3, "lo": 0},
                      "profNm": "새교수", "hgMjNm": "임베디드시스템공학과",
                      "hySeqGbn": "2", "cptnGbn": "전공심화",
                      "lsnTypeGbn": "강의(이론)", "inptGbn": "1",
                      "timeNm": " [27-101:월(1)(2)(3)]"
                    },
                    {
                      "yy": "2026", "tmGbn": "20", "haksuNo": "NEW001",
                      "scNm": "새과목", "hp": {"hi": 3, "lo": 0},
                      "profNm": "교수", "hgMjNm": "임베디드시스템공학과",
                      "hySeqGbn": "2", "cptnGbn": "전공심화",
                      "lsnTypeGbn": "강의(이론)", "inptGbn": "0",
                      "timeNm": ""
                    }
                  ]
                }
                """.getBytes());

        SubjectImportPlanResponse preview = service.preview(json, "2026-2", true);
        SubjectImportApplyResponse applied = service.apply(preview.planId(), List.of("AI001"));

        assertThat(applied.applied()).isTrue();
        assertThat(applied.verified()).isTrue();
        assertThat(applied.selectedCourseCount()).isEqualTo(1);
        assertThat(subjectRepository.findFirstByCourseCodeAndSemesterOrderByIdAsc("AI001", "2026-2"))
                .get()
                .satisfies(subject -> {
                    assertThat(subject.getSubjectName()).isEqualTo("변경과목");
                    assertThat(subject.getProfessor()).isEqualTo("새교수");
                    assertThat(subject.getSyllabusAvailable()).isTrue();
                });
        assertThat(subjectRepository.findFirstByCourseCodeAndSemesterOrderByIdAsc("NEW001", "2026-2"))
                .isEmpty();
    }

    @Test
    void appliesAndVerifiesMultiDayAndMultiBlockSchedules() throws Exception {
        subjectRepository.save(subject("AI002", "다요일기존과목"));
        when(timetableConflictResolutionService.reconcileImportChanges(
                anyList(), anyList(), anyList(), anyString()))
                .thenReturn(new TimetableReconciliationResult(
                        0, 0, 0, 0, 0, 0, List.of()));

        MockMultipartFile json = new MockMultipartFile(
                "file",
                "INU_강의계획서_2026_20.json",
                "application/json",
                """
                {
                  "rows": [
                    {
                      "yy": "2026", "tmGbn": "20", "haksuNo": "AI002",
                      "scNm": "다요일변경과목", "hp": {"hi": 3, "lo": 0},
                      "profNm": "새교수", "hgMjNm": "임베디드시스템공학과",
                      "hySeqGbn": "2", "cptnGbn": "전공심화",
                      "lsnTypeGbn": "강의(이론)", "inptGbn": "1",
                      "timeNm": " [27-201:월(9)] [27-202:월(11)] [27-101:화(1)(2)] [27-101:목(1)(2)]"
                    }
                  ]
                }
                """.getBytes());

        SubjectImportPlanResponse preview = service.preview(json, "2026-2", false);
        SubjectImportApplyResponse applied = service.apply(preview.planId(), List.of("AI002"));

        assertThat(applied.verified()).isTrue();
        assertThat(applied.verification())
                .singleElement()
                .satisfies(item -> assertThat(item.matched()).isTrue());
        assertThat(subjectRepository.findFirstByCourseCodeAndSemesterOrderByIdAsc("AI002", "2026-2"))
                .get()
                .extracting(subject -> subject.getSchedules().size())
                .isEqualTo(4);
    }

    private Subject subject(String code, String name) {
        return Subject.builder()
                .courseCode(code)
                .semester("2026-2")
                .active(true)
                .subjectName(name)
                .credits(3)
                .professor("기존교수")
                .department("임베디드시스템공학과")
                .grade(2)
                .subjectType(SubjectType.전심)
                .classMethod(ClassMethod.OFFLINE)
                .isNight(false)
                .syllabusAvailable(false)
                .build();
    }
}
