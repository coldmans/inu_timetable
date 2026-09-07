package inu.timetable.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import inu.timetable.controller.CsrfTestSupport.CsrfProof;
import inu.timetable.entity.AdminAccount;
import inu.timetable.entity.InquiryFaq;
import inu.timetable.repository.AdminAccountRepository;
import inu.timetable.repository.InquiryFaqRepository;
import inu.timetable.service.AdminAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

import static inu.timetable.controller.CsrfTestSupport.fetchCsrfProof;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class InquiryFaqControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InquiryFaqRepository inquiryFaqRepository;

    @Autowired
    private AdminAccountRepository adminAccountRepository;

    @Test
    void publicFaqListReturnsOnlyActiveFaqsInDisplayOrder() throws Exception {
        saveFaq("두 번째", "답변 2", 20, true);
        saveFaq("숨김", "숨긴 답변", 5, false);
        saveFaq("첫 번째", "답변 1", 10, true);

        mockMvc.perform(get("/api/inquiries/faqs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].question").value("첫 번째"))
                .andExpect(jsonPath("$[1].question").value("두 번째"));
    }

    @Test
    void adminFaqMutationRequiresAdminSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        CsrfProof csrfProof = fetchCsrfProof(mockMvc, objectMapper, session);

        mockMvc.perform(post("/admin/api/inquiry-faqs")
                .session(session)
                .cookie(csrfProof.cookie())
                .header("X-XSRF-TOKEN", csrfProof.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "question", "질문",
                        "answer", "답변"))))
                .andExpect(status().isForbidden());

        assertThat(inquiryFaqRepository.count()).isZero();
    }

    @Test
    void adminCanCreateUpdateAndDeleteFaq() throws Exception {
        MockHttpSession session = adminSession();
        CsrfProof csrfProof = fetchCsrfProof(mockMvc, objectMapper, session);

        String created = mockMvc.perform(post("/admin/api/inquiry-faqs")
                        .session(session)
                        .cookie(csrfProof.cookie())
                        .header("X-XSRF-TOKEN", csrfProof.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "question", "등록 질문",
                                "answer", "등록 답변",
                                "sortOrder", 10,
                                "active", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question").value("등록 질문"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long id = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(put("/admin/api/inquiry-faqs/{id}", id)
                        .session(session)
                        .cookie(csrfProof.cookie())
                        .header("X-XSRF-TOKEN", csrfProof.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "question", "수정 질문",
                                "answer", "수정 답변",
                                "sortOrder", 30,
                                "active", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question").value("수정 질문"))
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(delete("/admin/api/inquiry-faqs/{id}", id)
                        .session(session)
                        .cookie(csrfProof.cookie())
                        .header("X-XSRF-TOKEN", csrfProof.token()))
                .andExpect(status().isNoContent());

        assertThat(inquiryFaqRepository.findById(id)).isEmpty();
    }

    private InquiryFaq saveFaq(
            String question,
            String answer,
            int sortOrder,
            boolean active) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 29, 12, 0);
        return inquiryFaqRepository.save(InquiryFaq.builder()
                .question(question)
                .answer(answer)
                .sortOrder(sortOrder)
                .active(active)
                .createdAt(now)
                .updatedAt(now)
                .build());
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
