package inu.timetable.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
public class AdminAccessGuard {

    public static final String ADMIN_CSRF_HEADER = "X-Admin-Csrf";

    private final AdminAuthService adminAuthService;

    public void requireAuthenticated(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (!adminAuthService.isAuthenticated(session)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin login required");
        }
        if (requiresCsrfToken(request)) {
            String expectedToken = (String) session.getAttribute(AdminAuthService.SESSION_CSRF_TOKEN);
            String providedToken = request.getHeader(ADMIN_CSRF_HEADER);
            if (!StringUtils.hasText(expectedToken) || !expectedToken.equals(providedToken)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin CSRF token required");
            }
        }
    }

    public boolean isAuthenticated(HttpSession session) {
        return adminAuthService.isAuthenticated(session);
    }

    private boolean requiresCsrfToken(HttpServletRequest request) {
        String method = request.getMethod();
        return !"GET".equalsIgnoreCase(method)
                && !"HEAD".equalsIgnoreCase(method)
                && !"OPTIONS".equalsIgnoreCase(method);
    }
}
