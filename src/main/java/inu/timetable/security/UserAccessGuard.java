package inu.timetable.security;

import inu.timetable.enums.UserStatus;
import inu.timetable.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class UserAccessGuard {

    private final UserRepository userRepository;

    public Long requireMatchingUser(AuthenticatedUser authenticatedUser, Long requestedUserId) {
        if (authenticatedUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required");
        }
        if (!authenticatedUser.id().equals(requestedUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot access another user's data");
        }
        if (!userRepository.existsByIdAndStatus(authenticatedUser.id(), UserStatus.ACTIVE)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account is withdrawn");
        }
        return authenticatedUser.id();
    }
}
