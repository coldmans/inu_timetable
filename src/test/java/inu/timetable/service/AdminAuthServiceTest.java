package inu.timetable.service;

import inu.timetable.dto.AdminAuthResponse;
import inu.timetable.entity.AdminAccount;
import inu.timetable.repository.AdminAccountRepository;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminAuthServiceTest {

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private AdminAccountRepository adminAccountRepository;
    private AdminAuthService adminAuthService;

    @BeforeEach
    void setUp() {
        adminAccountRepository = mock(AdminAccountRepository.class);
        when(adminAccountRepository.findById(AdminAccount.SINGLETON_ID)).thenReturn(Optional.empty());
        when(adminAccountRepository.save(any(AdminAccount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        adminAuthService = new AdminAuthService(
                passwordEncoder,
                new InMemoryLoginAttemptStore(),
                adminAccountRepository);
        ReflectionTestUtils.setField(adminAuthService, "adminUsername", "jangboss02");
        ReflectionTestUtils.setField(adminAuthService, "adminPasswordHash", passwordEncoder.encode("secret"));
        ReflectionTestUtils.setField(adminAuthService, "legacyAdminPassword", "");
        ReflectionTestUtils.setField(adminAuthService, "maxFailures", 2);
        ReflectionTestUtils.setField(adminAuthService, "lockMinutes", 10L);
    }

    @Test
    void bootstrapLoginCreatesPasswordChangeRequiredSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        AdminAuthResponse response = adminAuthService.login("jangboss02", "secret", request);

        HttpSession session = request.getSession(false);
        assertThat(response.authenticated()).isTrue();
        assertThat(response.username()).isEqualTo("jangboss02");
        assertThat(response.passwordChangeRequired()).isTrue();
        assertThat(session).isNotNull();
        assertThat(session.getAttribute(AdminAuthService.SESSION_AUTHENTICATED)).isEqualTo(true);
        assertThat(session.getAttribute(AdminAuthService.SESSION_USERNAME)).isEqualTo("jangboss02");
        assertThat(session.getAttribute(AdminAuthService.SESSION_CREDENTIAL_VERSION)).isEqualTo(0L);
        assertThat(session.getAttribute(AdminAuthService.SESSION_PASSWORD_CHANGE_REQUIRED)).isEqualTo(true);
    }

    @Test
    void loginRejectsRepeatedFailuresWithTooManyRequests() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        assertThatThrownBy(() -> adminAuthService.login("jangboss02", "wrong", request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> adminAuthService.login("jangboss02", "wrong", request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> adminAuthService.login("jangboss02", "secret", request))
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

        AdminAuthResponse response = adminAuthService.login("jangboss02", "secret", request);

        assertThat(response.authenticated()).isTrue();
        assertThat(response.username()).isEqualTo("jangboss02");
    }

    @Test
    void loginAllowsLegacyPlainPasswordWhenHashIsNotConfigured() {
        ReflectionTestUtils.setField(adminAuthService, "adminPasswordHash", "");
        ReflectionTestUtils.setField(adminAuthService, "legacyAdminPassword", "legacy-secret");
        MockHttpServletRequest request = new MockHttpServletRequest();

        AdminAuthResponse response = adminAuthService.login("jangboss02", "legacy-secret", request);

        assertThat(response.authenticated()).isTrue();
        assertThat(response.username()).isEqualTo("jangboss02");
    }

    @Test
    void persistedAccountTakesPrecedenceOverBootstrapCredentials() {
        AdminAccount account = AdminAccount.builder()
                .id(AdminAccount.SINGLETON_ID)
                .username("changed-admin")
                .passwordHash(passwordEncoder.encode("changed-secret"))
                .passwordChangeRequired(false)
                .credentialVersion(3L)
                .build();
        when(adminAccountRepository.findById(AdminAccount.SINGLETON_ID))
                .thenReturn(Optional.of(account));
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThatThrownBy(() -> adminAuthService.login("jangboss02", "secret", request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        AdminAuthResponse response =
                adminAuthService.login("changed-admin", "changed-secret", new MockHttpServletRequest());

        assertThat(response.username()).isEqualTo("changed-admin");
        assertThat(response.passwordChangeRequired()).isFalse();
    }

    @Test
    void changeCredentialsPersistsOnlyBcryptHashAndUnlocksSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        adminAuthService.login("jangboss02", "secret", request);

        AdminAuthResponse response = adminAuthService.changeCredentials(
                "secret",
                "jangboss02",
                "Stronger-password-123!",
                "Stronger-password-123!",
                request);

        ArgumentCaptor<AdminAccount> captor = ArgumentCaptor.forClass(AdminAccount.class);
        verify(adminAccountRepository).save(captor.capture());
        AdminAccount saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("jangboss02");
        assertThat(saved.getPasswordHash()).isNotEqualTo("Stronger-password-123!");
        assertThat(passwordEncoder.matches("Stronger-password-123!", saved.getPasswordHash())).isTrue();
        assertThat(saved.isPasswordChangeRequired()).isFalse();
        assertThat(saved.getCredentialVersion()).isEqualTo(1L);
        assertThat(response.passwordChangeRequired()).isFalse();
        assertThat(request.getSession(false)
                .getAttribute(AdminAuthService.SESSION_PASSWORD_CHANGE_REQUIRED)).isEqualTo(false);
        assertThat(request.getSession(false)
                .getAttribute(AdminAuthService.SESSION_CREDENTIAL_VERSION)).isEqualTo(1L);
    }

    @Test
    void changeCredentialsRejectsWeakPassword() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        adminAuthService.login("jangboss02", "secret", request);

        assertThatThrownBy(() -> adminAuthService.changeCredentials(
                "secret",
                "jangboss02",
                "1234",
                "1234",
                request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void migratedSessionMustMatchCredentialVersion() {
        AdminAccount account = AdminAccount.builder()
                .id(AdminAccount.SINGLETON_ID)
                .username("jangboss02")
                .passwordHash(passwordEncoder.encode("changed-secret"))
                .passwordChangeRequired(false)
                .credentialVersion(4L)
                .build();
        when(adminAccountRepository.findById(AdminAccount.SINGLETON_ID))
                .thenReturn(Optional.of(account));

        MockHttpServletRequest staleRequest = new MockHttpServletRequest();
        assertThat(adminAuthService.restoreMigratedSession(
                "jangboss02", 3L, staleRequest)).isFalse();
        assertThat(staleRequest.getSession(false)).isNull();

        MockHttpServletRequest currentRequest = new MockHttpServletRequest();
        assertThat(adminAuthService.restoreMigratedSession(
                "jangboss02", 4L, currentRequest)).isTrue();
        assertThat(adminAuthService.hasAdminAccess(currentRequest.getSession(false))).isTrue();
    }
}
