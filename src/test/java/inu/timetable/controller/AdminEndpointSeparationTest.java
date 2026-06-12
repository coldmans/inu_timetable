package inu.timetable.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AdminEndpointSeparationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("기존 public 관리자 인증 경로는 노출하지 않는다")
    void legacyPublicAdminAuthPathIsNotMapped() throws Exception {
        mockMvc.perform(post("/api/admin/auth/login"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("기존 public 과목 import 경로는 노출하지 않는다")
    void legacyPublicSubjectImportPathIsNotMapped() throws Exception {
        mockMvc.perform(post("/api/subjects/import/apply"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("기존 public 과목 생성 경로는 생성 API로 동작하지 않는다")
    void legacyPublicSubjectCreatePathIsNotMapped() throws Exception {
        mockMvc.perform(post("/api/subjects"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("public 과목 단건 관리 조회는 노출하지 않는다")
    void publicSubjectManagementDetailPathIsNotMapped() throws Exception {
        mockMvc.perform(get("/api/subjects/1"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("admin 과목 관리 경로는 인증 게이트 뒤에 있다")
    void adminSubjectManagementPathRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/admin/api/subjects/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("admin 인증 상태 경로는 admin namespace에 있다")
    void adminAuthPathRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/admin/api/auth/me"))
                .andExpect(status().isForbidden());
    }
}
