package inu.timetable.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import inu.timetable.controller.CsrfTestSupport.CsrfProof;
import inu.timetable.entity.AdminAccount;
import inu.timetable.repository.AdminAccountRepository;
import inu.timetable.service.AdminAuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static inu.timetable.controller.CsrfTestSupport.fetchCsrfProof;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
@TestPropertySource(properties = {
        "admin.username=jangboss02",
        "admin.password=bootstrap-secret",
        "admin.password-hash="
})
class AdminAccountIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AdminAccountRepository adminAccountRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Test
    @DisplayName("최초 관리자 로그인은 계정 변경 화면으로 이동한다")
    void bootstrapLoginRedirectsToAccountPage() throws Exception {
        MockHttpSession session = new MockHttpSession();
        CsrfProof csrf = fetchCsrfProof(mockMvc, objectMapper, session);

        mockMvc.perform(post("/admin/login")
                        .session(session)
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .param("username", "jangboss02")
                        .param("password", "bootstrap-secret"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/account"));
    }

    @Test
    @DisplayName("변경 완료된 관리자 로그인은 대시보드로 이동한다")
    void persistedAdminLoginRedirectsToDashboard() throws Exception {
        adminAccountRepository.save(AdminAccount.builder()
                .id(AdminAccount.SINGLETON_ID)
                .username("jangboss02")
                .passwordHash(passwordEncoder.encode("Stronger-password-123!"))
                .passwordChangeRequired(false)
                .credentialVersion(1L)
                .build());
        MockHttpSession session = new MockHttpSession();
        CsrfProof csrf = fetchCsrfProof(mockMvc, objectMapper, session);

        mockMvc.perform(post("/admin/login")
                        .session(session)
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .param("username", "jangboss02")
                        .param("password", "Stronger-password-123!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/dashboard"));
    }

    @Test
    @DisplayName("최초 로그인 세션은 계정 변경 전까지 대시보드에 접근할 수 없다")
    void bootstrapSessionCannotAccessDashboardBeforeCredentialChange() throws Exception {
        mockMvc.perform(get("/admin/dashboard").session(bootstrapSession()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/account"));
    }

    @Test
    @DisplayName("계정 변경 화면은 CSRF 폼과 비밀번호 정책 안내를 제공한다")
    void accountPageContainsCsrfFormAndPasswordPolicy() throws Exception {
        mockMvc.perform(get("/admin/account").session(bootstrapSession()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("12자 이상")));
    }

    @Test
    @DisplayName("관리자는 최초 비밀번호를 강한 비밀번호로 변경하고 대시보드로 이동한다")
    void changeCredentialsPersistsAccountAndRedirectsToDashboard() throws Exception {
        MockHttpSession session = bootstrapSession();
        CsrfProof csrf = fetchCsrfProof(mockMvc, objectMapper, session);

        mockMvc.perform(post("/admin/account")
                        .session(session)
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .param("currentPassword", "bootstrap-secret")
                        .param("newUsername", "jangboss02")
                        .param("newPassword", "Stronger-password-123!")
                        .param("newPasswordConfirm", "Stronger-password-123!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/dashboard"));

        AdminAccount saved = adminAccountRepository.findById(AdminAccount.SINGLETON_ID).orElseThrow();
        assertThat(saved.getUsername()).isEqualTo("jangboss02");
        assertThat(saved.getPasswordHash()).isNotEqualTo("Stronger-password-123!");
        assertThat(passwordEncoder.matches("Stronger-password-123!", saved.getPasswordHash())).isTrue();
        assertThat(saved.isPasswordChangeRequired()).isFalse();
    }

    private MockHttpSession bootstrapSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AdminAuthService.SESSION_AUTHENTICATED, true);
        session.setAttribute(AdminAuthService.SESSION_USERNAME, "jangboss02");
        session.setAttribute(AdminAuthService.SESSION_CREDENTIAL_VERSION, 0L);
        session.setAttribute(AdminAuthService.SESSION_PASSWORD_CHANGE_REQUIRED, true);
        return session;
    }
}
