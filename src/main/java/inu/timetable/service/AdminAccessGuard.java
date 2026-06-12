package inu.timetable.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
public class AdminAccessGuard {

    private final AdminAuthService adminAuthService;

    public void requireAuthenticated(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (!adminAuthService.isAuthenticated(session)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin login required");
        }
    }

    public boolean isAuthenticated(HttpSession session) {
        return adminAuthService.isAuthenticated(session);
    }
}
