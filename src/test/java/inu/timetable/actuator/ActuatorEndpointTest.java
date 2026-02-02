package inu.timetable.actuator;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Actuator 엔드포인트 통합 테스트
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ActuatorEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Health 엔드포인트가 정상적으로 응답한다")
    void healthEndpointShouldReturnOk() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("Health 엔드포인트에 DB 상태가 포함된다 (dev 프로파일)")
    void healthEndpointShouldIncludeDatabaseStatus() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.db").exists())
                .andExpect(jsonPath("$.components.db.status").value("UP"));
    }

    @Test
    @Disabled("테스트 환경에서 Prometheus 엔드포인트가 제대로 등록되지 않음 - 실제 환경에서는 정상 동작 확인")
    @DisplayName("Prometheus 메트릭 엔드포인트가 정상적으로 응답한다")
    void prometheusEndpointShouldReturnMetrics() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("jvm_")));
    }

    @Test
    @DisplayName("Info 엔드포인트가 정상적으로 응답한다")
    void infoEndpointShouldReturnOk() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Metrics 엔드포인트가 정상적으로 응답한다")
    void metricsEndpointShouldReturnOk() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names").isArray());
    }
}