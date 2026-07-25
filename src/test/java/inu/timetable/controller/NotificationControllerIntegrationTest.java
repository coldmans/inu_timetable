package inu.timetable.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import inu.timetable.entity.UserNotification;
import inu.timetable.repository.UserNotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static inu.timetable.controller.CsrfTestSupport.fetchCsrfProof;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class NotificationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserNotificationRepository userNotificationRepository;

    @Test
    @DisplayName("미인증 유저는 알림 조회가 거부된다")
    void unreadNotificationsRequireLogin() throws Exception {
        mockMvc.perform(get("/api/notifications/unread"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("로그인 유저는 본인 미읽음 알림만 생성순으로 조회한다")
    void getUnreadNotificationsReturnsOwnUnreadOnly() throws Exception {
        RegisteredUser registeredUser = registerAndReturnSession();
        RegisteredUser otherUser = registerAndReturnSession();

        saveNotification(registeredUser.userId(), "첫 번째 알림", LocalDateTime.of(2026, 7, 25, 9, 0));
        saveNotification(registeredUser.userId(), "두 번째 알림", LocalDateTime.of(2026, 7, 25, 10, 0));
        saveNotification(otherUser.userId(), "남의 알림", LocalDateTime.of(2026, 7, 25, 11, 0));

        mockMvc.perform(get("/api/notifications/unread").session(registeredUser.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].message").value("첫 번째 알림"))
                .andExpect(jsonPath("$[1].message").value("두 번째 알림"));
    }

    @Test
    @DisplayName("읽음 처리하면 미읽음 전부 read_at 이 채워지고 이후 조회는 비어 있다")
    void markAllReadFillsReadAtAndEmptiesUnreadList() throws Exception {
        RegisteredUser registeredUser = registerAndReturnSession();
        saveNotification(registeredUser.userId(), "시간 변경 알림 1", LocalDateTime.of(2026, 7, 25, 9, 0));
        saveNotification(registeredUser.userId(), "시간 변경 알림 2", LocalDateTime.of(2026, 7, 25, 10, 0));
        CsrfTestSupport.CsrfProof csrfProof = fetchCsrfProof(mockMvc, objectMapper, registeredUser.session());

        mockMvc.perform(post("/api/notifications/read")
                        .session(registeredUser.session())
                        .cookie(csrfProof.cookie())
                        .header("X-XSRF-TOKEN", csrfProof.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readCount").value(2));

        mockMvc.perform(get("/api/notifications/unread").session(registeredUser.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        assertThat(userNotificationRepository.findAll())
                .filteredOn(notification -> notification.getUserId().equals(registeredUser.userId()))
                .allSatisfy(notification -> assertThat(notification.getReadAt()).isNotNull());
    }

    @Test
    @DisplayName("읽음 처리 POST 는 CSRF 토큰 없이는 거부된다")
    void markAllReadRequiresCsrfToken() throws Exception {
        RegisteredUser registeredUser = registerAndReturnSession();

        mockMvc.perform(post("/api/notifications/read").session(registeredUser.session()))
                .andExpect(status().isForbidden());
    }

    private void saveNotification(Long userId, String message, LocalDateTime createdAt) {
        userNotificationRepository.save(UserNotification.builder()
                .userId(userId)
                .message(message)
                .createdAt(createdAt)
                .build());
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

    private record RegisteredUser(MockHttpSession session, Long userId) {
    }
}
