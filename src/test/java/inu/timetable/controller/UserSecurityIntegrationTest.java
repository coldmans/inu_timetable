package inu.timetable.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import inu.timetable.entity.User;
import inu.timetable.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class UserSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    @Qualifier("userPasswordEncoder")
    private PasswordEncoder passwordEncoder;

    @Test
    void userSessionProtectsPrivateUserDataEndpoints() throws Exception {
        RegisteredUser registeredUser = registerAndReturnSession();

        mockMvc.perform(get("/api/wishlist/user/%d".formatted(registeredUser.userId()))
                        .param("semester", "2026-1"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/wishlist/user/%d".formatted(registeredUser.userId() + 1000))
                        .session(registeredUser.session())
                        .param("semester", "2026-1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void privateMutationRequiresCsrfToken() throws Exception {
        RegisteredUser registeredUser = registerAndReturnSession();

        mockMvc.perform(post("/api/timetable-combination/generate")
                        .session(registeredUser.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": %d,
                                  "semester": "2026-1",
                                  "targetCredits": 18
                                }
                                """.formatted(registeredUser.userId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void legacySha256PasswordMigratesToBcryptOnSuccessfulLogin() throws Exception {
        String username = "legacy-" + UUID.randomUUID();
        String legacyPasswordHash = sha256("password123");
        userRepository.save(User.builder()
                .username(username)
                .password(legacyPasswordHash)
                .grade(2)
                .major("컴퓨터공학부")
                .build());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "password123"
                                }
                                """.formatted(username)))
                .andExpect(status().isOk());

        User migratedUser = userRepository.findByUsername(username).orElseThrow();
        assertThat(migratedUser.getPassword()).isNotEqualTo(legacyPasswordHash);
        assertThat(passwordEncoder.matches("password123", migratedUser.getPassword())).isTrue();
    }

    @Test
    void csrfTokenAllowsPrivateMutationAfterLogin() throws Exception {
        RegisteredUser registeredUser = registerAndReturnSession();
        CsrfProof csrfProof = fetchCsrfProof(registeredUser.session());

        mockMvc.perform(post("/api/timetable-combination/generate")
                        .session(registeredUser.session())
                        .cookie(csrfProof.cookie())
                        .header("X-XSRF-TOKEN", csrfProof.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": %d,
                                  "semester": "2026-1",
                                  "targetCredits": 18
                                }
                                """.formatted(registeredUser.userId())))
                .andExpect(status().isOk());
    }

    private RegisteredUser registerAndReturnSession() throws Exception {
        String username = "student-" + UUID.randomUUID();
        var result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "password123",
                                  "grade": 2,
                                  "major": "컴퓨터공학부"
                                }
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode responseBody = objectMapper.readTree(result.getResponse().getContentAsString());
        return new RegisteredUser(
                (MockHttpSession) result.getRequest().getSession(false),
                responseBody.get("id").asLong());
    }

    private CsrfProof fetchCsrfProof(MockHttpSession session) throws Exception {
        var result = mockMvc.perform(get("/api/auth/csrf").session(session))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode responseBody = objectMapper.readTree(result.getResponse().getContentAsString());
        return new CsrfProof(
                responseBody.get("token").asText(),
                result.getResponse().getCookie("XSRF-TOKEN"));
    }

    private String sha256(String value) {
        try {
            byte[] hashedBytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte hashedByte : hashedBytes) {
                builder.append(String.format("%02x", hashedByte));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record CsrfProof(String token, Cookie cookie) {
    }

    private record RegisteredUser(MockHttpSession session, Long userId) {
    }
}
