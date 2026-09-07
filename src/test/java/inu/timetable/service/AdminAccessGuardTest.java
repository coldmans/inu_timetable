package inu.timetable.service;

import inu.timetable.entity.AdminAccount;
import inu.timetable.repository.AdminAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminAccessGuardTest {

    private AdminAuthService adminAuthService;
    private AdminAccessGuard adminAccessGuard;

    @BeforeEach
    void setUp() {
        AdminAccountRepository adminAccountRepository = mock(AdminAccountRepository.class);
        when(adminAccountRepository.findById(AdminAccount.SINGLETON_ID))
                .thenReturn(Optional.of(AdminAccount.builder()
                        .id(AdminAccount.SINGLETON_ID)
                        .username("admin")
                        .passwordHash("unused")
                        .passwordChangeRequired(false)
                        .credentialVersion(1L)
                        .build()));
        adminAuthService = new AdminAuthService(
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4),
                new InMemoryLoginAttemptStore(),
                adminAccountRepository);
        ReflectionTestUtils.setField(adminAuthService, "adminUsername", "admin");
        adminAccessGuard = new AdminAccessGuard(adminAuthService);
    }

    @Test
    void authenticatedRequestIsAllowed() {
        MockHttpServletRequest request = authenticatedRequest("POST");

        assertThatCode(() -> adminAccessGuard.requireAuthenticated(request))
                .doesNotThrowAnyException();
    }

    @Test
    void unauthenticatedRequestIsRejected() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/api/subjects");

        assertThatThrownBy(() -> adminAccessGuard.requireAuthenticated(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void passwordChangeRequiredRequestIsRejected() {
        MockHttpServletRequest request = authenticatedRequest("POST");
        request.getSession(false)
                .setAttribute(AdminAuthService.SESSION_PASSWORD_CHANGE_REQUIRED, true);

        assertThatThrownBy(() -> adminAccessGuard.requireAuthenticated(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    private MockHttpServletRequest authenticatedRequest(String method) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/admin/api/subjects");
        request.getSession(true).setAttribute(AdminAuthService.SESSION_AUTHENTICATED, true);
        request.getSession(false).setAttribute(AdminAuthService.SESSION_USERNAME, "admin");
        request.getSession(false).setAttribute(AdminAuthService.SESSION_CREDENTIAL_VERSION, 1L);
        request.getSession(false).setAttribute(AdminAuthService.SESSION_PASSWORD_CHANGE_REQUIRED, false);
        return request;
    }
}
