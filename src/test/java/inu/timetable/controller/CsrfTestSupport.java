package inu.timetable.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

final class CsrfTestSupport {

    private CsrfTestSupport() {
    }

    static CsrfProof fetchCsrfProof(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            MockHttpSession session) throws Exception {
        var result = mockMvc.perform(get("/api/auth/csrf").session(session))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode responseBody = objectMapper.readTree(result.getResponse().getContentAsString());
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        return new CsrfProof(responseBody.get("token").asText(), cookie);
    }

    record CsrfProof(String token, Cookie cookie) {
    }
}
