package inu.timetable.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import inu.timetable.entity.Schedule;
import inu.timetable.entity.Subject;
import inu.timetable.enums.ClassMethod;
import inu.timetable.enums.SubjectType;
import inu.timetable.repository.SubjectRepository;
import inu.timetable.repository.WishlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class DevSessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private WishlistRepository wishlistRepository;

    @BeforeEach
    void setUp() {
        if (subjectRepository.countByActiveTrue() == 0) {
            subjectRepository.saveAll(List.of(
                    subject("이전학기개발테스트", "2024-2", "월", 1.0, 2.0),
                    subject("레거시현재학기개발테스트", null, "화", 7.0, 8.0),
                    subject("개발테스트자료구조", "2026-1", "월", 1.0, 2.0),
                    subject("개발테스트운영체제", "2026-1", "화", 3.0, 4.0),
                    subject("개발테스트네트워크", "2026-1", "수", 5.0, 6.0),
                    subject("개발테스트데이터베이스", "2026-1", "목", 7.0, 8.0),
                    subject("개발테스트알고리즘", "2026-1", "금", 1.0, 2.0),
                    subject("개발테스트소프트웨어공학", "2026-1", "월", 5.0, 6.0)
            ));
        }
    }

    @Test
    void devSessionCreatesAuthenticatedUserAndSeedsWishlist() throws Exception {
        var result = mockMvc.perform(post("/api/dev/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "semester": "2026-1",
                                  "reset": true
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        JsonNode responseBody = objectMapper.readTree(result.getResponse().getContentAsString());
        long userId = responseBody.get("user").get("id").asLong();

        assertThat(session).isNotNull();
        assertThat(responseBody.get("wishlistCount").asInt()).isGreaterThan(0);
        assertThat(responseBody.get("seededWishlistCount").asInt()).isGreaterThan(0);
        assertThat(wishlistRepository.findByUserIdAndSemesterWithSubjectAndSchedules(userId, "2026-1"))
                .allSatisfy(item -> assertThat(item.getSubject().getSemester()).isIn("2026-1", null))
                .noneSatisfy(item -> assertThat(item.getSubject().getSemester()).isEqualTo("2024-2"));

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/wishlist/user/%d".formatted(userId))
                        .session(session)
                        .param("semester", "2026-1"))
                .andExpect(status().isOk());
    }

    private Subject subject(String name, String semester, String dayOfWeek, double startTime, double endTime) {
        Subject subject = Subject.builder()
                .courseCode("DEV-" + UUID.randomUUID().toString().substring(0, 8))
                .semester(semester)
                .active(true)
                .subjectName(name)
                .credits(3)
                .professor("테스트교수")
                .isNight(false)
                .subjectType(SubjectType.전심)
                .classMethod(ClassMethod.OFFLINE)
                .grade(3)
                .department("컴퓨터공학부")
                .build();
        Schedule schedule = Schedule.builder()
                .subject(subject)
                .dayOfWeek(dayOfWeek)
                .startTime(startTime)
                .endTime(endTime)
                .build();
        subject.getSchedules().add(schedule);
        return subject;
    }
}
