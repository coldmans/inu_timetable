package inu.timetable.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import inu.timetable.entity.User;
import inu.timetable.enums.UserStatus;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "user.login.max-failures=2",
        "user.login.lock-minutes=10"
})
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
    void repeatedUserLoginFailuresAreRateLimited() throws Exception {
        RegisteredUser registeredUser = registerAndReturnSession();

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "username": "%s",
                                      "password": "wrong-password"
                                    }
                                    """.formatted(registeredUser.username())))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "password123"
                                }
                                """.formatted(registeredUser.username())))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429));
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
    void registerStoresMajorSelections() throws Exception {
        String username = "student-" + UUID.randomUUID();
        var result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "password123",
                                  "grade": 2,
                                  "major": "컴퓨터공학부",
                                  "majors": [
                                    {"type": "PRIMARY", "department": "컴퓨터공학부"},
                                    {"type": "DOUBLE", "department": "데이터과학과"},
                                    {"type": "MINOR", "department": "경영학부"}
                                  ]
                                }
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.major").value("컴퓨터공학부"))
                .andExpect(jsonPath("$.majors[0].type").value("PRIMARY"))
                .andExpect(jsonPath("$.majors[0].department").value("컴퓨터공학부"))
                .andExpect(jsonPath("$.majors[1].type").value("DOUBLE"))
                .andExpect(jsonPath("$.majors[1].department").value("데이터과학과"))
                .andExpect(jsonPath("$.majors[2].type").value("MINOR"))
                .andExpect(jsonPath("$.majors[2].department").value("경영학부"))
                .andReturn();

        JsonNode responseBody = objectMapper.readTree(result.getResponse().getContentAsString());
        User user = userRepository.findByIdAndStatus(responseBody.get("id").asLong(), UserStatus.ACTIVE).orElseThrow();
        assertThat(user.getMajor()).isEqualTo("컴퓨터공학부");
        assertThat(user.getUserMajors())
                .extracting(userMajor -> userMajor.getType() + ":" + userMajor.getDepartment())
                .containsExactlyInAnyOrder(
                        "PRIMARY:컴퓨터공학부",
                        "DOUBLE:데이터과학과",
                        "MINOR:경영학부"
                );
    }

    @Test
    void updateProfileChangesGradeAndMajorSelectionsForAuthenticatedUser() throws Exception {
        String username = "student-" + UUID.randomUUID();
        var registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "password123",
                                  "grade": 2,
                                  "major": "컴퓨터공학부",
                                  "majors": [
                                    {"type": "PRIMARY", "department": "컴퓨터공학부"},
                                    {"type": "DOUBLE", "department": "경영학부"},
                                    {"type": "MINOR", "department": "국어국문학과"}
                                  ]
                                }
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) registerResult.getRequest().getSession(false);
        JsonNode registerBody = objectMapper.readTree(registerResult.getResponse().getContentAsString());
        long userId = registerBody.get("id").asLong();
        CsrfProof csrfProof = fetchCsrfProof(session);

        mockMvc.perform(patch("/api/auth/me")
                        .session(session)
                        .cookie(csrfProof.cookie())
                        .header("X-XSRF-TOKEN", csrfProof.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "grade": 3,
                                  "major": "데이터과학과",
                                  "majors": [
                                    {"type": "PRIMARY", "department": "데이터과학과"},
                                    {"type": "DOUBLE", "department": "경영학부"},
                                    {"type": "MINOR", "department": "국어국문학과"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grade").value(3))
                .andExpect(jsonPath("$.major").value("데이터과학과"))
                .andExpect(jsonPath("$.majors[0].type").value("PRIMARY"))
                .andExpect(jsonPath("$.majors[0].department").value("데이터과학과"))
                .andExpect(jsonPath("$.majors[1].type").value("DOUBLE"))
                .andExpect(jsonPath("$.majors[1].department").value("경영학부"))
                .andExpect(jsonPath("$.majors[2].type").value("MINOR"))
                .andExpect(jsonPath("$.majors[2].department").value("국어국문학과"));

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grade").value(3))
                .andExpect(jsonPath("$.major").value("데이터과학과"));

        User user = userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE).orElseThrow();
        assertThat(user.getGrade()).isEqualTo(3);
        assertThat(user.getMajor()).isEqualTo("데이터과학과");
        assertThat(user.getUserMajors())
                .extracting(userMajor -> userMajor.getType() + ":" + userMajor.getDepartment())
                .containsExactlyInAnyOrder(
                        "PRIMARY:데이터과학과",
                        "DOUBLE:경영학부",
                        "MINOR:국어국문학과"
                );
    }

    @Test
    void duplicateRegisterReturnsConflictErrorResponse() throws Exception {
        RegisteredUser registeredUser = registerAndReturnSession();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "password123",
                                  "grade": 2,
                                  "major": "컴퓨터공학부"
                                }
                                """.formatted(registeredUser.username())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("이미 사용 중인 아이디입니다. 다른 아이디를 입력해주세요."));
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

    @Test
    void privateApiValidationReturnsStandardBadRequestResponse() throws Exception {
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
                                  "semester": "2026-1"
                                }
                                """.formatted(registeredUser.userId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("targetCredits 값이 필요합니다."));
    }

    @Test
    void wishlistDebugEndpointIsNotMappedForAuthenticatedUsers() throws Exception {
        RegisteredUser registeredUser = registerAndReturnSession();

        mockMvc.perform(get("/api/wishlist/debug/%d".formatted(registeredUser.userId()))
                        .session(registeredUser.session()))
                .andExpect(status().isNotFound());
    }

    @Test
    void withdrawSoftDeletesUserInvalidatesSessionAndBlocksLogin() throws Exception {
        RegisteredUser registeredUser = registerAndReturnSession();
        long userCountAfterRegister = userRepository.count();
        CsrfProof csrfProof = fetchCsrfProof(registeredUser.session());

        mockMvc.perform(delete("/api/auth/me")
                        .session(registeredUser.session())
                        .cookie(csrfProof.cookie())
                        .header("X-XSRF-TOKEN", csrfProof.token()))
                .andExpect(status().isOk());

        assertThat(userRepository.count()).isEqualTo(userCountAfterRegister);
        User withdrawnUser = userRepository.findById(registeredUser.userId()).orElseThrow();
        assertThat(withdrawnUser.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(withdrawnUser.getDeletedAt()).isNotNull();
        assertThat(withdrawnUser.getUsername()).startsWith("withdrawn_user_%d_".formatted(registeredUser.userId()));
        assertThat(withdrawnUser.getNickname()).isNull();
        assertThat(passwordEncoder.matches("password123", withdrawnUser.getPassword())).isFalse();
        assertThat(withdrawnUser.getGrade()).isEqualTo(2);
        assertThat(withdrawnUser.getMajor()).isEqualTo("컴퓨터공학부");

        mockMvc.perform(get("/api/auth/me").session(registeredUser.session()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "password123"
                                }
                                """.formatted(registeredUser.username())))
                .andExpect(status().isUnauthorized());
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
                responseBody.get("id").asLong(),
                username);
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

    private record RegisteredUser(MockHttpSession session, Long userId, String username) {
    }
}
