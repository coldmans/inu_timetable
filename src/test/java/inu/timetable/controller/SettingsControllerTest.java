package inu.timetable.controller;

import inu.timetable.entity.AppSetting;
import inu.timetable.repository.AppSettingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class SettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppSettingRepository appSettingRepository;

    @Test
    @DisplayName("현재 학기 설정이 없으면 기본값 2026-2를 반환한다")
    void getCurrentSemesterReturnsFallbackWhenSettingMissing() throws Exception {
        mockMvc.perform(get("/api/settings/current-semester"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.semester").value("2026-2"));
    }

    @Test
    @DisplayName("현재 학기 설정이 저장되어 있으면 저장된 값을 반환한다")
    void getCurrentSemesterReturnsStoredValue() throws Exception {
        appSettingRepository.save(AppSetting.builder()
                .settingKey("current_semester")
                .settingValue("2026-2")
                .updatedAt(LocalDateTime.now())
                .build());

        mockMvc.perform(get("/api/settings/current-semester"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.semester").value("2026-2"));
    }
}
