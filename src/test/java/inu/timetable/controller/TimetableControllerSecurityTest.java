package inu.timetable.controller;

import inu.timetable.security.AuthenticatedUser;
import inu.timetable.security.UserAccessGuard;
import inu.timetable.service.TimetableService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TimetableControllerSecurityTest {

    private TimetableService timetableService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        timetableService = mock(TimetableService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TimetableController(timetableService, new UserAccessGuard()))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getUserTimetableRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/timetable/user/1").param("semester", "2026-1"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(timetableService);
    }

    @Test
    void getUserTimetableRejectsDifferentUserId() throws Exception {
        authenticate(2L);

        mockMvc.perform(get("/api/timetable/user/1")
                        .param("semester", "2026-1"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(timetableService);
    }

    @Test
    void getUserTimetableAllowsOwnUserId() throws Exception {
        authenticate(1L);
        when(timetableService.getUserTimetable(1L, "2026-1")).thenReturn(List.of());

        mockMvc.perform(get("/api/timetable/user/1")
                        .param("semester", "2026-1"))
                .andExpect(status().isOk());

        verify(timetableService).getUserTimetable(1L, "2026-1");
    }

    private void authenticate(Long userId) {
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(
                userId, "student", "encoded-password", List.of());
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        authenticatedUser, null, authenticatedUser.getAuthorities()));
    }
}
