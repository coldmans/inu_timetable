package inu.timetable.controller;

import inu.timetable.entity.User;
import inu.timetable.repository.UserRepository;
import inu.timetable.security.AuthenticatedUser;
import inu.timetable.service.SessionMigrationPrincipal;
import inu.timetable.service.SessionMigrationTokenService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "session.shared.enabled=true",
        "session.bridge.mode=consume"
})
class SessionMigrationBridgeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder userPasswordEncoder;

    @Autowired
    private SessionMigrationTokenService tokenService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void oneTimeBridgeTokenCreatesReusableJdbcUserSession() throws Exception {
        String username = "bridge-" + UUID.randomUUID();
        User user = userRepository.save(User.builder()
                .username(username)
                .password(userPasswordEncoder.encode("password123"))
                .grade(2)
                .major("컴퓨터공학부")
                .build());
        String token = tokenService.issue(
                SessionMigrationPrincipal.user(user.getId()),
                Duration.ofMinutes(30));

        var migrated = mockMvc.perform(get("/api/auth/me")
                        .cookie(new MockCookie("INU_SESSION_BRIDGE", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andReturn();

        Cookie sessionCookie = migrated.getResponse().getCookie("SESSION");
        assertThat(sessionCookie).isNotNull();
        assertThat(migrated.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .anyMatch(header -> header.startsWith("INU_SESSION_BRIDGE=")
                        && header.contains("Max-Age=0"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM spring_session",
                Integer.class)).isGreaterThanOrEqualTo(1);
        byte[] serializedSecurityContext = jdbcTemplate.queryForObject("""
                SELECT attribute_bytes
                FROM spring_session_attributes
                WHERE attribute_name = 'SPRING_SECURITY_CONTEXT'
                ORDER BY session_primary_id DESC
                LIMIT 1
                """, byte[].class);
        SecurityContext storedContext;
        try (ObjectInputStream input =
                     new ObjectInputStream(new ByteArrayInputStream(serializedSecurityContext))) {
            storedContext = (SecurityContext) input.readObject();
        }
        AuthenticatedUser storedPrincipal =
                (AuthenticatedUser) storedContext.getAuthentication().getPrincipal();
        assertThat(storedPrincipal.password()).isNull();

        mockMvc.perform(get("/api/auth/me").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username));

        mockMvc.perform(get("/api/auth/me")
                        .cookie(new MockCookie("INU_SESSION_BRIDGE", token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bridgeAlsoMigratesAdminSession() throws Exception {
        String token = tokenService.issue(
                SessionMigrationPrincipal.admin("admin"),
                Duration.ofMinutes(30));

        var migrated = mockMvc.perform(get("/admin/api/auth/me")
                        .cookie(new MockCookie("INU_SESSION_BRIDGE", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.username").value("admin"))
                .andReturn();

        Cookie sessionCookie = migrated.getResponse().getCookie("SESSION");
        assertThat(sessionCookie).isNotNull();

        mockMvc.perform(get("/admin/api/auth/me").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true));
    }
}
