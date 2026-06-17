package inu.timetable.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import inu.timetable.entity.AdminAuditLog;
import inu.timetable.repository.AdminAuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static inu.timetable.controller.CsrfTestSupport.fetchCsrfProof;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "admin.username=admin",
        "admin.password=test-admin-password",
        "admin.password-hash="
})
class AdminAuditLogIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AdminAuditLogRepository adminAuditLogRepository;

    @BeforeEach
    void setUp() {
        adminAuditLogRepository.deleteAll();
    }

    @Test
    @DisplayName("관리자 로그인 성공은 감사 로그에 남는다")
    void adminLoginSuccessWritesAuditLog() throws Exception {
        MockHttpSession session = new MockHttpSession();
        CsrfTestSupport.CsrfProof csrfProof = fetchCsrfProof(mockMvc, objectMapper, session);

        mockMvc.perform(post("/admin/api/auth/login")
                        .session(session)
                        .cookie(csrfProof.cookie())
                        .header("X-XSRF-TOKEN", csrfProof.token())
                        .header("User-Agent", "MockMvc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "admin",
                                  "password": "test-admin-password"
                                }
                                """))
                .andExpect(status().isOk());

        List<AdminAuditLog> logs = adminAuditLogRepository.findAll();
        assertThat(logs).hasSize(1);
        AdminAuditLog log = logs.get(0);
        assertThat(log.getAdminUsername()).isEqualTo("admin");
        assertThat(log.getMethod()).isEqualTo("POST");
        assertThat(log.getPath()).isEqualTo("/admin/api/auth/login");
        assertThat(log.getStatus()).isEqualTo(200);
        assertThat(log.getSuccess()).isTrue();
        assertThat(log.getUserAgent()).isEqualTo("MockMvc");
        assertThat(log.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("관리자 인증 실패 응답도 감사 로그에 남는다")
    void adminAuthenticationFailureWritesAuditLog() throws Exception {
        mockMvc.perform(get("/admin/api/auth/me"))
                .andExpect(status().isForbidden());

        List<AdminAuditLog> logs = adminAuditLogRepository.findAll();
        assertThat(logs).hasSize(1);
        AdminAuditLog log = logs.get(0);
        assertThat(log.getAdminUsername()).isNull();
        assertThat(log.getMethod()).isEqualTo("GET");
        assertThat(log.getPath()).isEqualTo("/admin/api/auth/me");
        assertThat(log.getStatus()).isEqualTo(403);
        assertThat(log.getSuccess()).isFalse();
    }
}
