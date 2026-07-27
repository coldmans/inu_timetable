package inu.timetable.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "session.shared.enabled=false",
        "session.bridge.mode=issue"
})
class SessionMigrationBridgeIssueIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void loginIssuesOpaqueBridgeCookieAndStoresOnlyItsHash() throws Exception {
        String username = "issue-" + UUID.randomUUID();
        var register = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "username", username,
                                "password", "password123",
                                "grade", 2,
                                "major", "컴퓨터공학부"))))
                .andExpect(status().isOk())
                .andReturn();

        Cookie bridgeCookie = register.getResponse().getCookie("INU_SESSION_BRIDGE");
        assertThat(bridgeCookie).isNotNull();
        assertThat(bridgeCookie.isHttpOnly()).isTrue();
        assertThat(bridgeCookie.getValue()).doesNotContain(username);

        String storedHash = jdbcTemplate.queryForObject("""
                SELECT token_hash
                FROM session_migration_tokens
                WHERE principal_type = 'USER'
                ORDER BY created_at DESC
                LIMIT 1
                """, String.class);
        assertThat(storedHash).isEqualTo(sha256(bridgeCookie.getValue()));
        assertThat(storedHash).doesNotContain(bridgeCookie.getValue());

        MockHttpSession session = (MockHttpSession) register.getRequest().getSession(false);
        var csrf = mockMvc.perform(get("/api/auth/csrf")
                        .session(session)
                        .cookie(bridgeCookie))
                .andExpect(status().isOk())
                .andReturn();
        String csrfToken = objectMapper.readTree(csrf.getResponse().getContentAsString())
                .get("token")
                .asText();

        var logout = mockMvc.perform(post("/api/auth/logout")
                        .session(session)
                        .cookie(bridgeCookie, csrf.getResponse().getCookie("XSRF-TOKEN"))
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM session_migration_tokens WHERE token_hash = ?",
                Integer.class,
                storedHash)).isZero();
        assertThat(logout.getResponse().getHeaders("Set-Cookie"))
                .anyMatch(header -> header.startsWith("INU_SESSION_BRIDGE=")
                        && header.contains("Max-Age=0"));
    }

    private String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
