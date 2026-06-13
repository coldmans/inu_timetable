package inu.timetable.security;

import inu.timetable.enums.UserStatus;
import inu.timetable.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserAccessGuardTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserAccessGuard userAccessGuard = new UserAccessGuard(userRepository);

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
        when(userRepository.existsByIdAndStatus(1L, UserStatus.ACTIVE)).thenReturn(true);

        assertThat(userAccessGuard.requireMatchingUser(authenticatedUser, 1L)).isEqualTo(1L);
    }

    @Test
    void requireMatchingUserRejectsWithdrawnUser() {
        AuthenticatedUser authenticatedUser = authenticatedUser(1L);
        when(userRepository.existsByIdAndStatus(1L, UserStatus.ACTIVE)).thenReturn(false);

        assertThatThrownBy(() -> userAccessGuard.requireMatchingUser(authenticatedUser, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401 UNAUTHORIZED");
    }

    private AuthenticatedUser authenticatedUser(Long userId) {
        return new AuthenticatedUser(userId, "student", "encoded-password", java.util.List.of());
    }
}
