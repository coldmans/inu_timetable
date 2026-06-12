package inu.timetable.service;

import inu.timetable.dto.AdminAuthResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminAuthServiceTest {

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private AdminAuthService adminAuthService;

    @BeforeEach
    void setUp() {
        adminAuthService = new AdminAuthService(passwordEncoder);
        ReflectionTestUtils.setField(adminAuthService, "adminUsername", "admin");
        ReflectionTestUtils.setField(adminAuthService, "adminPasswordHash", passwordEncoder.encode("secret"));
        ReflectionTestUtils.setField(adminAuthService, "maxFailures", 2);
        ReflectionTestUtils.setField(adminAuthService, "lockMinutes", 10L);
    }

    @Test
    void loginCreatesAdminSessionAndCsrfToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        AdminAuthResponse response = adminAuthService.login("admin", "secret", request);

        HttpSession session = request.getSession(false);
        assertThat(response.authenticated()).isTrue();
        assertThat(response.username()).isEqualTo("admin");
        assertThat(response.csrfToken()).isNotBlank();
        assertThat(session).isNotNull();
        assertThat(session.getAttribute(AdminAuthService.SESSION_AUTHENTICATED)).isEqualTo(true);
        assertThat(session.getAttribute(AdminAuthService.SESSION_CSRF_TOKEN)).isEqualTo(response.csrfToken());
    }

    @Test
    void loginRejectsRepeatedFailuresWithTooManyRequests() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        assertThatThrownBy(() -> adminAuthService.login("admin", "wrong", request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> adminAuthService.login("admin", "wrong", request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> adminAuthService.login("admin", "secret", request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void failuresForDifferentUsernameDoNotLockConfiguredAdminUsername() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        assertThatThrownBy(() -> adminAuthService.login("other", "wrong", request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> adminAuthService.login("other", "wrong", request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        AdminAuthResponse response = adminAuthService.login("admin", "secret", request);

        assertThat(response.authenticated()).isTrue();
        assertThat(response.username()).isEqualTo("admin");
    }
}
