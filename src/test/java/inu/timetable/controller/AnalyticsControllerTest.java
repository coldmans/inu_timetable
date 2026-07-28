package inu.timetable.controller;

import inu.timetable.dto.AnalyticsDashboardResponse;
import inu.timetable.service.AdminAccessGuard;
import inu.timetable.service.AnalyticsDashboardService;
import inu.timetable.service.AnalyticsService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalyticsControllerTest {

    @Test
    void dashboardRequiresAdminAndDelegatesSelectedRange() {
        AnalyticsService analyticsService = mock(AnalyticsService.class);
        AnalyticsDashboardService dashboardService = mock(AnalyticsDashboardService.class);
        AdminAccessGuard accessGuard = mock(AdminAccessGuard.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        AnalyticsDashboardResponse expected = mock(AnalyticsDashboardResponse.class);
        when(dashboardService.dashboard("6h")).thenReturn(expected);
        AnalyticsController controller = new AnalyticsController(
                analyticsService,
                dashboardService,
                accessGuard);

        AnalyticsDashboardResponse actual = controller.dashboard(request, "6h");

        assertThat(actual).isSameAs(expected);
        verify(accessGuard).requireAuthenticated(request);
        verify(dashboardService).dashboard("6h");
    }
}
