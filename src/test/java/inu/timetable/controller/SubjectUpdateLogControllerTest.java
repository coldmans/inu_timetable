package inu.timetable.controller;

import inu.timetable.entity.SubjectUpdateLog;
import inu.timetable.repository.SubjectUpdateLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class SubjectUpdateLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubjectUpdateLogRepository subjectUpdateLogRepository;

    @Test
    @DisplayName("업데이트 일지는 인증 없이 최신순으로 조회된다")
    void getUpdateLogsIsPublicAndOrderedByAppliedAtDesc() throws Exception {
        saveLog(LocalDateTime.of(2026, 7, 24, 10, 0), "2026-1", 5, 2, 1);
        saveLog(LocalDateTime.of(2026, 7, 26, 10, 0), "2026-2", 10, 3, 0);
        saveLog(LocalDateTime.of(2026, 7, 25, 10, 0), "2026-1", 7, 0, 2);

        mockMvc.perform(get("/api/subjects/update-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].semester").value("2026-2"))
                .andExpect(jsonPath("$[0].sourceFormat").value("OFFICIAL_TIMETABLE"))
                .andExpect(jsonPath("$[0].addedCount").value(10))
                .andExpect(jsonPath("$[0].modifiedCount").value(3))
                .andExpect(jsonPath("$[0].removedCount").value(0))
                .andExpect(jsonPath("$[1].addedCount").value(7))
                .andExpect(jsonPath("$[2].addedCount").value(5));
    }

    @Test
    @DisplayName("limit 파라미터를 적용하고 최대 50으로 클램프한다")
    void getUpdateLogsClampsLimit() throws Exception {
        for (int index = 0; index < 55; index++) {
            saveLog(LocalDateTime.of(2026, 7, 1, 0, 0).plusMinutes(index), "2026-1", index, 0, 0);
        }

        mockMvc.perform(get("/api/subjects/update-logs").param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(get("/api/subjects/update-logs").param("limit", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(50)));
    }

    private void saveLog(
            LocalDateTime appliedAt, String semester, int addedCount, int modifiedCount, int removedCount) {
        subjectUpdateLogRepository.save(SubjectUpdateLog.builder()
                .appliedAt(appliedAt)
                .semester(semester)
                .sourceFormat("OFFICIAL_TIMETABLE")
                .addedCount(addedCount)
                .modifiedCount(modifiedCount)
                .removedCount(removedCount)
                .build());
    }
}
