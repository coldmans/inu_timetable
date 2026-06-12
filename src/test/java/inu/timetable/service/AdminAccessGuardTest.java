package inu.timetable.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminAccessGuardTest {

    private AdminAuthService adminAuthService;
    private AdminAccessGuard adminAccessGuard;

    @BeforeEach
    void setUp() {
        adminAuthService = new AdminAuthService(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4));
        adminAccessGuard = new AdminAccessGuard(adminAuthService);
    }

    @Test
    void getRequestAllowsAuthenticatedSessionWithoutCsrfHeader() {
        MockHttpServletRequest request = authenticatedRequest("GET", "token");

        assertThatCode(() -> adminAccessGuard.requireAuthenticated(request))
                .doesNotThrowAnyException();
    }

    @Test
    void postRequestRequiresCsrfHeader() {
        MockHttpServletRequest request = authenticatedRequest("POST", "token");

        assertThatThrownBy(() -> adminAccessGuard.requireAuthenticated(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void postRequestAllowsMatchingCsrfHeader() {
        MockHttpServletRequest request = authenticatedRequest("POST", "token");
        request.addHeader(AdminAccessGuard.ADMIN_CSRF_HEADER, "token");

        assertThatCode(() -> adminAccessGuard.requireAuthenticated(request))
                .doesNotThrowAnyException();
    }

    private MockHttpServletRequest authenticatedRequest(String method, String csrfToken) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/admin/api/subjects");
        request.getSession(true).setAttribute(AdminAuthService.SESSION_AUTHENTICATED, true);
        request.getSession(false).setAttribute(AdminAuthService.SESSION_CSRF_TOKEN, csrfToken);
        return request;
    }
}
