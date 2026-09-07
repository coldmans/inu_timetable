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
        if (!adminAuthService.hasAdminAccess(session)) {
            String reason = adminAuthService.isPasswordChangeRequired(session)
                    ? "Admin credential change required"
                    : "Admin login required";
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, reason);
        }
    }

    public boolean isAuthenticated(HttpSession session) {
        return adminAuthService.hasAdminAccess(session);
    }
}
