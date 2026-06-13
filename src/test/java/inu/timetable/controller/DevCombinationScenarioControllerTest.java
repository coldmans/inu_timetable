package inu.timetable.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import inu.timetable.repository.WishlistRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class DevCombinationScenarioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WishlistRepository wishlistRepository;

    @ParameterizedTest
    @ValueSource(ints = {6, 12, 18, 24, 30})
    void createsAuthenticatedCombinationScenario(int wishlistSize) throws Exception {
        var result = mockMvc.perform(post("/api/dev/combination-scenario")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "scenario-%d",
                                  "semester": "2026-1",
                                  "wishlistSize": %d,
                                  "slotCount": 6,
                                  "reset": true
                                }
                                """.formatted(wishlistSize, wishlistSize)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        JsonNode responseBody = objectMapper.readTree(result.getResponse().getContentAsString());
        long userId = responseBody.get("user").get("id").asLong();

        assertThat(session).isNotNull();
        assertThat(responseBody.get("wishlistCount").asInt()).isEqualTo(wishlistSize);
        assertThat(wishlistRepository.findByUserIdAndSemesterWithSubjectAndSchedules(userId, "2026-1"))
                .hasSize(wishlistSize);

        CsrfTestSupport.CsrfProof csrfProof = CsrfTestSupport.fetchCsrfProof(mockMvc, objectMapper, session);
        mockMvc.perform(post("/api/timetable-combination/generate")
                        .session(session)
                        .cookie(csrfProof.cookie())
                        .header("X-XSRF-TOKEN", csrfProof.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": %d,
                                  "semester": "2026-1",
                                  "targetCredits": 18,
                                  "maxCombinations": 20,
                                  "freeDays": []
                                }
                                """.formatted(userId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk());
    }
}
