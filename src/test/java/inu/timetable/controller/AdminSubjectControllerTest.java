package inu.timetable.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import inu.timetable.controller.CsrfTestSupport.CsrfProof;
import inu.timetable.dto.SubjectManagementRequest;
import inu.timetable.entity.Schedule;
import inu.timetable.entity.Subject;
import inu.timetable.enums.ClassMethod;
import inu.timetable.enums.SubjectType;
import inu.timetable.repository.SubjectRepository;
import inu.timetable.service.AdminAuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static java.util.Map.entry;
import static inu.timetable.controller.CsrfTestSupport.fetchCsrfProof;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class AdminSubjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SubjectRepository subjectRepository;

    @Test
    @DisplayName("관리자 과목 추가 API는 과목과 시간표를 함께 저장한다")
    void createSubjectSavesSubjectAndSchedules() throws Exception {
        MockHttpSession session = adminSession();
        CsrfProof csrfProof = fetchCsrfProof(mockMvc, objectMapper, session);
        SubjectManagementRequest request = new SubjectManagementRequest(
                "CSE9999001",
                "2026-2",
                true,
                "컨트롤러추가테스트",
                3,
                "김테스트",
                "컴퓨터공학부",
                2,
                SubjectType.전심,
                ClassMethod.OFFLINE,
                false,
                List.of(new SubjectManagementRequest.ScheduleRequest("월", 1.0, 2.5)));

        mockMvc.perform(post("/admin/api/subjects")
                .session(session)
                .cookie(csrfProof.cookie())
                .header("X-XSRF-TOKEN", csrfProof.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subjectName").value("컨트롤러추가테스트"))
                .andExpect(jsonPath("$.courseCode").value("CSE9999001"))
                .andExpect(jsonPath("$.semester").value("2026-2"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.schedules[0].dayOfWeek").value("월"));

        Subject savedSubject = onlySubjectNamed("컨트롤러추가테스트");
        assertThat(savedSubject.getCourseCode()).isEqualTo("CSE9999001");
        assertThat(savedSubject.getSemester()).isEqualTo("2026-2");
        assertThat(savedSubject.getSubjectType()).isEqualTo(SubjectType.전심);
        assertThat(savedSubject.getClassMethod()).isEqualTo(ClassMethod.OFFLINE);
        assertThat(savedSubject.getActive()).isTrue();
        assertThat(savedSubject.getSchedules()).hasSize(1);

        Schedule schedule = savedSubject.getSchedules().get(0);
        assertThat(schedule.getSubject()).isSameAs(savedSubject);
        assertThat(schedule.getDayOfWeek()).isEqualTo("월");
        assertThat(schedule.getStartTime()).isEqualTo(1.0);
        assertThat(schedule.getEndTime()).isEqualTo(2.5);
    }

    @Test
    @DisplayName("수동 과목 추가 API는 시간 문자열을 파싱해 시간표를 저장한다")
    void manualAddParsesTimeStringAndSavesSchedules() throws Exception {
        MockHttpSession session = adminSession();
        CsrfProof csrfProof = fetchCsrfProof(mockMvc, objectMapper, session);
        List<Map<String, Object>> request = List.of(Map.ofEntries(
                entry("subjectName", "수동추가테스트"),
                entry("credits", 2),
                entry("professor", "박테스트"),
                entry("isNight", false),
                entry("subjectType", "전핵"),
                entry("classMethod", "BLENDED"),
                entry("grade", 1),
                entry("department", "컴퓨터공학부"),
                entry("timeString", "월 1A-2B 수 3-4")));

        mockMvc.perform(post("/admin/api/subjects/manual")
                .session(session)
                .cookie(csrfProof.cookie())
                .header("X-XSRF-TOKEN", csrfProof.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].subjectName").value("수동추가테스트"))
                .andExpect(jsonPath("$[0].schedules.length()").value(2));

        Subject savedSubject = onlySubjectNamed("수동추가테스트");
        assertThat(savedSubject.getSubjectType()).isEqualTo(SubjectType.전핵);
        assertThat(savedSubject.getClassMethod()).isEqualTo(ClassMethod.BLENDED);
        assertThat(savedSubject.getSchedules()).hasSize(2);
        assertThat(savedSubject.getSchedules())
                .extracting(Schedule::getDayOfWeek, Schedule::getStartTime, Schedule::getEndTime)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("월", 1.0, 3.0),
                        org.assertj.core.groups.Tuple.tuple("수", 3.0, 5.0));
    }

    @Test
    @DisplayName("관리자 과목 추가 API는 Spring CSRF 토큰이 없으면 저장하지 않는다")
    void createSubjectRejectsMissingSpringCsrfToken() throws Exception {
        SubjectManagementRequest request = new SubjectManagementRequest(
                "CSE9999002",
                "2026-2",
                true,
                "CSRF차단테스트",
                3,
                "이테스트",
                "컴퓨터공학부",
                2,
                SubjectType.전심,
                ClassMethod.OFFLINE,
                false,
                List.of(new SubjectManagementRequest.ScheduleRequest("화", 2.0, 3.0)));

        mockMvc.perform(post("/admin/api/subjects")
                .session(adminSession())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        assertThat(subjectsNamed("CSRF차단테스트")).isEmpty();
    }

    @Test
    @DisplayName("실제 공식 Excel 파일 preview는 전체 과목 행을 읽는다")
    void previewActualOfficialExcelFile() throws Exception {
        MockHttpSession session = adminSession();
        CsrfProof csrfProof = fetchCsrfProof(mockMvc, objectMapper, session);

        mockMvc.perform(multipart("/admin/api/subjects/import/preview")
                .file(actualOfficialExcelFile())
                .param("semester", "2025-2")
                .session(session)
                .cookie(csrfProof.cookie())
                .header("X-XSRF-TOKEN", csrfProof.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(false))
                .andExpect(jsonPath("$.semester").value("2025-2"))
                .andExpect(jsonPath("$.totalRows").value(2392))
                .andExpect(jsonPath("$.addedCount").value(2392))
                .andExpect(jsonPath("$.modifiedCount").value(0))
                .andExpect(jsonPath("$.removedCount").value(0));

        assertThat(subjectRepository.count()).isZero();
    }

    @Test
    @DisplayName("실제 공식 Excel 파일 apply는 과목과 시간표를 DB에 저장한다")
    void applyActualOfficialExcelFile() throws Exception {
        MockHttpSession session = adminSession();
        CsrfProof csrfProof = fetchCsrfProof(mockMvc, objectMapper, session);

        mockMvc.perform(multipart("/admin/api/subjects/import/apply")
                .file(actualOfficialExcelFile())
                .param("semester", "2025-2")
                .param("deactivateMissing", "true")
                .session(session)
                .cookie(csrfProof.cookie())
                .header("X-XSRF-TOKEN", csrfProof.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(true))
                .andExpect(jsonPath("$.semester").value("2025-2"))
                .andExpect(jsonPath("$.totalRows").value(2392))
                .andExpect(jsonPath("$.addedCount").value(2392));

        assertThat(subjectRepository.countByActiveTrue()).isEqualTo(2392);

        List<Subject> savedSubjects = subjectRepository.findImportCandidatesBySemester("2025-2");
        assertThat(savedSubjects).hasSize(2392);
        assertThat(savedSubjects.stream().filter(subject -> subject.getSchedules().isEmpty()).count()).isEqualTo(107);
        assertThat(savedSubjects.stream().mapToLong(subject -> subject.getSchedules().size()).sum()).isEqualTo(3492);
        assertThat(savedSubjects.stream().flatMap(subject -> subject.getSchedules().stream()))
                .allSatisfy(schedule -> {
                    assertThat(schedule.getDayOfWeek()).isIn("월", "화", "수", "목", "금", "토", "일");
                    assertThat(schedule.getEndTime()).isGreaterThan(schedule.getStartTime());
                    assertThat(schedule.getStartTime()).isGreaterThanOrEqualTo(0.0);
                    assertThat(schedule.getEndTime()).isLessThanOrEqualTo(22.0);
                });
        assertThat(savedSubjects)
                .anySatisfy(subject -> {
                    assertThat(subject.getCourseCode()).isEqualTo("0009062001");
                    assertThat(subject.getSubjectName()).isEqualTo("RISE");
                    assertThat(subject.getSemester()).isEqualTo("2025-2");
                    assertThat(subject.getDepartment()).isEqualTo("국어국문학과");
                    assertThat(subject.getSubjectType()).isEqualTo(SubjectType.전심);
                    assertThat(subject.getSchedules()).hasSize(1);
                    assertThat(subject.getSchedules().get(0).getDayOfWeek()).isEqualTo("월");
                });
        assertSchedules(savedSubjects, "0009062001", tuple("월", 10.0, 13.0));
        assertSchedules(savedSubjects, "0004890001", tuple("금", 4.0, 7.0));
        assertSchedules(savedSubjects, "0004894002", tuple("수", 2.5, 5.5));
        assertSchedules(savedSubjects, "0004905001",
                tuple("월", 5.5, 7.0),
                tuple("목", 8.5, 10.0));
        assertSchedules(savedSubjects, "0011255002", tuple("목", 10.0, 14.0));
    }

    private MockHttpSession adminSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AdminAuthService.SESSION_AUTHENTICATED, true);
        session.setAttribute(AdminAuthService.SESSION_USERNAME, "admin");
        return session;
    }

    private MockMultipartFile actualOfficialExcelFile() throws Exception {
        InputStream inputStream = getClass().getResourceAsStream("/sample_timetable.xlsx");
        assertThat(inputStream).isNotNull();
        return new MockMultipartFile(
                "file",
                "sample_timetable.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                inputStream);
    }

    private Subject onlySubjectNamed(String subjectName) {
        List<Subject> subjects = subjectsNamed(subjectName);
        assertThat(subjects).hasSize(1);
        return subjects.get(0);
    }

    private void assertSchedules(
            List<Subject> subjects,
            String courseCode,
            org.assertj.core.groups.Tuple... expectedSchedules) {
        Subject subject = subjects.stream()
                .filter(candidate -> courseCode.equals(candidate.getCourseCode()))
                .findFirst()
                .orElseThrow();

        assertThat(subject.getSchedules())
                .extracting(Schedule::getDayOfWeek, Schedule::getStartTime, Schedule::getEndTime)
                .containsExactlyInAnyOrder(expectedSchedules);
    }

    private List<Subject> subjectsNamed(String subjectName) {
        return subjectRepository.findAllWithSchedules().stream()
                .filter(subject -> subjectName.equals(subject.getSubjectName()))
                .toList();
    }
}
