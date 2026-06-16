package inu.timetable.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static inu.timetable.controller.CsrfTestSupport.fetchCsrfProof;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AdminEndpointSeparationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("기존 public 관리자 인증 경로는 노출하지 않는다")
    void legacyPublicAdminAuthPathIsNotMapped() throws Exception {
        mockMvc.perform(post("/api/admin/auth/login"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("기존 public 과목 import 경로는 노출하지 않는다")
    void legacyPublicSubjectImportPathIsNotMapped() throws Exception {
        mockMvc.perform(post("/api/subjects/import/apply"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("기존 public 과목 생성 경로는 생성 API로 동작하지 않는다")
    void legacyPublicSubjectCreatePathIsNotMapped() throws Exception {
        mockMvc.perform(post("/api/subjects"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("public 과목 단건 관리 조회는 노출하지 않는다")
    void publicSubjectManagementDetailPathIsNotMapped() throws Exception {
        mockMvc.perform(get("/api/subjects/1"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("admin 과목 관리 경로는 인증 게이트 뒤에 있다")
    void adminSubjectManagementPathRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/admin/api/subjects/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("admin 인증 상태 경로는 admin namespace에 있다")
    void adminAuthPathRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/admin/api/auth/me"))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/admin/api/analyze-missing",
            "/admin/api/excel-headers"
    })
    @DisplayName("admin 조회 API는 인증 게이트 뒤에 있다")
    void adminReadApiPathsRequireAuthentication(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/admin/api/database/add-column",
            "/admin/api/insert-missing"
    })
    @DisplayName("admin 변경 API는 CSRF 토큰이 있어도 관리자 세션 없이는 차단한다")
    void adminMutationApiPathsRequireAuthenticationAfterCsrf(String path) throws Exception {
        MockHttpSession session = new MockHttpSession();
        CsrfTestSupport.CsrfProof csrfProof = fetchCsrfProof(mockMvc, objectMapper, session);

        mockMvc.perform(post(path)
                .session(session)
                .cookie(csrfProof.cookie())
                .header("X-XSRF-TOKEN", csrfProof.token()))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/admin/api/pdf/upload",
            "/admin/api/excel/upload",
            "/admin/api/validation/pdf",
            "/admin/api/validation/excel",
            "/admin/api/subjects/import/preview",
            "/admin/api/subjects/import/apply"
    })
    @DisplayName("admin 파일 API는 CSRF 토큰이 있어도 관리자 세션 없이는 차단한다")
    void adminMultipartApiPathsRequireAuthenticationAfterCsrf(String path) throws Exception {
        MockHttpSession session = new MockHttpSession();
        CsrfTestSupport.CsrfProof csrfProof = fetchCsrfProof(mockMvc, objectMapper, session);

        mockMvc.perform(multipart(path)
                .file(emptyUpload())
                .param("semester", "2026-1")
                .session(session)
                .cookie(csrfProof.cookie())
                .header("X-XSRF-TOKEN", csrfProof.token()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("admin 과목 생성 API는 CSRF 토큰이 있어도 관리자 세션 없이는 차단한다")
    void adminSubjectCreateRequiresAuthenticationAfterCsrf() throws Exception {
        MockHttpSession session = new MockHttpSession();
        CsrfTestSupport.CsrfProof csrfProof = fetchCsrfProof(mockMvc, objectMapper, session);

        mockMvc.perform(post("/admin/api/subjects")
                .session(session)
                .cookie(csrfProof.cookie())
                .header("X-XSRF-TOKEN", csrfProof.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(subjectRequestBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("admin 과목 수정 API는 CSRF 토큰이 있어도 관리자 세션 없이는 차단한다")
    void adminSubjectUpdateRequiresAuthenticationAfterCsrf() throws Exception {
        MockHttpSession session = new MockHttpSession();
        CsrfTestSupport.CsrfProof csrfProof = fetchCsrfProof(mockMvc, objectMapper, session);

        mockMvc.perform(put("/admin/api/subjects/1")
                .session(session)
                .cookie(csrfProof.cookie())
                .header("X-XSRF-TOKEN", csrfProof.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(subjectRequestBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("admin 과목 삭제 API는 CSRF 토큰이 있어도 관리자 세션 없이는 차단한다")
    void adminSubjectDeleteRequiresAuthenticationAfterCsrf() throws Exception {
        MockHttpSession session = new MockHttpSession();
        CsrfTestSupport.CsrfProof csrfProof = fetchCsrfProof(mockMvc, objectMapper, session);

        mockMvc.perform(delete("/admin/api/subjects/1")
                .session(session)
                .cookie(csrfProof.cookie())
                .header("X-XSRF-TOKEN", csrfProof.token()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("admin 수동 과목 추가 API는 CSRF 토큰이 있어도 관리자 세션 없이는 차단한다")
    void adminSubjectManualCreateRequiresAuthenticationAfterCsrf() throws Exception {
        MockHttpSession session = new MockHttpSession();
        CsrfTestSupport.CsrfProof csrfProof = fetchCsrfProof(mockMvc, objectMapper, session);

        mockMvc.perform(post("/admin/api/subjects/manual")
                .session(session)
                .cookie(csrfProof.cookie())
                .header("X-XSRF-TOKEN", csrfProof.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        [
                          {
                            "subjectName": "인증차단테스트",
                            "credits": 3,
                            "professor": "김테스트",
                            "isNight": false,
                            "subjectType": "전심",
                            "classMethod": "OFFLINE",
                            "grade": 2,
                            "department": "컴퓨터공학부",
                            "timeString": "월 1-2"
                          }
                        ]
                        """))
                .andExpect(status().isForbidden());
    }

    private MockMultipartFile emptyUpload() {
        return new MockMultipartFile(
                "file",
                "empty.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[0]);
    }

    private String subjectRequestBody() {
        return """
                {
                  "courseCode": "CSE9999001",
                  "semester": "2026-1",
                  "active": true,
                  "subjectName": "인증차단테스트",
                  "credits": 3,
                  "professor": "김테스트",
                  "department": "컴퓨터공학부",
                  "grade": 2,
                  "subjectType": "전심",
                  "classMethod": "OFFLINE",
                  "isNight": false,
                  "schedules": [
                    {
                      "dayOfWeek": "월",
                      "startTime": 1.0,
                      "endTime": 2.0
                    }
                  ]
                }
                """;
    }
}
