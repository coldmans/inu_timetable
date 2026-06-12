package inu.timetable.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "admin.username=admin",
        "admin.password=test-admin-password",
        "admin.password-hash="
})
class AdminCsrfIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("관리자 API 로그인은 Spring CSRF 토큰을 요구한다")
    void adminApiLoginRequiresSpringCsrfToken() throws Exception {
        mockMvc.perform(post("/admin/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "username": "admin",
                          "password": "test-admin-password"
                        }
                        """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("관리자 API 로그인은 Spring CSRF 토큰이 있으면 성공한다")
    void adminApiLoginAllowsSpringCsrfToken() throws Exception {
        MockHttpSession session = new MockHttpSession();
        CsrfProof csrfProof = fetchCsrfProof(session);

        mockMvc.perform(post("/admin/api/auth/login")
                .session(session)
                .cookie(csrfProof.cookie())
                .header("X-XSRF-TOKEN", csrfProof.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "username": "admin",
                          "password": "test-admin-password"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.username").value("admin"));
    }

    @Test
    @DisplayName("관리자 로그인 폼은 Spring CSRF hidden input을 포함한다")
    void adminLoginFormIncludesSpringCsrfInput() throws Exception {
        mockMvc.perform(get("/admin/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    private CsrfProof fetchCsrfProof(MockHttpSession session) throws Exception {
        var result = mockMvc.perform(get("/api/auth/csrf").session(session))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode responseBody = objectMapper.readTree(result.getResponse().getContentAsString());
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        return new CsrfProof(responseBody.get("token").asText(), cookie);
    }

    private record CsrfProof(String token, Cookie cookie) {
    }
}
