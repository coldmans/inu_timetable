package inu.timetable.security;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserAccessGuardTest {

    private final UserAccessGuard userAccessGuard = new UserAccessGuard();

    @Test
    void requireMatchingUserRejectsMissingPrincipal() {
        assertThatThrownBy(() -> userAccessGuard.requireMatchingUser(null, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401 UNAUTHORIZED");
    }

    @Test
    void requireMatchingUserRejectsDifferentUserId() {
        AuthenticatedUser authenticatedUser = authenticatedUser(2L);

        assertThatThrownBy(() -> userAccessGuard.requireMatchingUser(authenticatedUser, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403 FORBIDDEN");
    }

    @Test
    void requireMatchingUserReturnsAuthenticatedUserId() {
        AuthenticatedUser authenticatedUser = authenticatedUser(1L);

        assertThat(userAccessGuard.requireMatchingUser(authenticatedUser, 1L)).isEqualTo(1L);
    }

    private AuthenticatedUser authenticatedUser(Long userId) {
        return new AuthenticatedUser(userId, "student", "encoded-password", java.util.List.of());
    }
}
