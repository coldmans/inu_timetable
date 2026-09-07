package inu.timetable.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import inu.timetable.controller.CsrfTestSupport.CsrfProof;
import inu.timetable.entity.AdminAccount;
import inu.timetable.entity.AppSetting;
import inu.timetable.repository.AdminAccountRepository;
import inu.timetable.repository.AppSettingRepository;
import inu.timetable.service.AdminAuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static inu.timetable.controller.CsrfTestSupport.fetchCsrfProof;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class AdminSettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppSettingRepository appSettingRepository;

    @Autowired
    private AdminAccountRepository adminAccountRepository;

    @Test
    @DisplayName("관리자 인증이 없으면 현재 학기 변경을 거부한다")
    void updateCurrentSemesterRejectsUnauthenticatedRequest() throws Exception {
        MockHttpSession session = new MockHttpSession();
        CsrfProof csrfProof = fetchCsrfProof(mockMvc, objectMapper, session);

        mockMvc.perform(put("/admin/api/settings/current-semester")
                .session(session)
                .cookie(csrfProof.cookie())
                .header("X-XSRF-TOKEN", csrfProof.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("semester", "2026-2"))))
                .andExpect(status().isForbidden());

        assertThat(appSettingRepository.findById("current_semester")).isEmpty();
    }

    @Test
    @DisplayName("관리자 인증 시 현재 학기를 변경하고 저장한다")
    void updateCurrentSemesterSavesValueWhenAuthenticated() throws Exception {
        MockHttpSession session = adminSession();
        CsrfProof csrfProof = fetchCsrfProof(mockMvc, objectMapper, session);

        mockMvc.perform(put("/admin/api/settings/current-semester")
                .session(session)
                .cookie(csrfProof.cookie())
                .header("X-XSRF-TOKEN", csrfProof.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("semester", "2026-2"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.semester").value("2026-2"));

        AppSetting saved = appSettingRepository.findById("current_semester").orElseThrow();
        assertThat(saved.getSettingValue()).isEqualTo("2026-2");
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("현재 학기를 여러 번 변경하면 마지막 값으로 덮어쓴다")
    void updateCurrentSemesterOverwritesExistingValue() throws Exception {
        MockHttpSession session = adminSession();
        CsrfProof csrfProof = fetchCsrfProof(mockMvc, objectMapper, session);

        mockMvc.perform(put("/admin/api/settings/current-semester")
                .session(session)
                .cookie(csrfProof.cookie())
                .header("X-XSRF-TOKEN", csrfProof.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("semester", "2026-1"))))
                .andExpect(status().isOk());

        mockMvc.perform(put("/admin/api/settings/current-semester")
                .session(session)
                .cookie(csrfProof.cookie())
                .header("X-XSRF-TOKEN", csrfProof.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("semester", "2026-2"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.semester").value("2026-2"));

        AppSetting saved = appSettingRepository.findById("current_semester").orElseThrow();
        assertThat(saved.getSettingValue()).isEqualTo("2026-2");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-3", "abc", "2026", "26-1", "2026-12"})
    @DisplayName("잘못된 학기 형식이면 400을 반환하고 저장하지 않는다")
    void updateCurrentSemesterRejectsInvalidFormat(String invalidSemester) throws Exception {
        MockHttpSession session = adminSession();
        CsrfProof csrfProof = fetchCsrfProof(mockMvc, objectMapper, session);

        mockMvc.perform(put("/admin/api/settings/current-semester")
                .session(session)
                .cookie(csrfProof.cookie())
                .header("X-XSRF-TOKEN", csrfProof.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("semester", invalidSemester))))
                .andExpect(status().isBadRequest());

        assertThat(appSettingRepository.findById("current_semester")).isEmpty();
    }

    private MockHttpSession adminSession() {
        adminAccountRepository.findById(AdminAccount.SINGLETON_ID)
                .orElseGet(() -> adminAccountRepository.save(AdminAccount.builder()
                        .id(AdminAccount.SINGLETON_ID)
                        .username("admin")
                        .passwordHash("unused-in-session-test")
                        .passwordChangeRequired(false)
                        .credentialVersion(1L)
                        .build()));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AdminAuthService.SESSION_AUTHENTICATED, true);
        session.setAttribute(AdminAuthService.SESSION_USERNAME, "admin");
        session.setAttribute(AdminAuthService.SESSION_CREDENTIAL_VERSION, 1L);
        session.setAttribute(AdminAuthService.SESSION_PASSWORD_CHANGE_REQUIRED, false);
        return session;
    }
}
