package inu.timetable.service;

import inu.timetable.dto.AdminAuthResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AdminAuthService {

    public static final String SESSION_AUTHENTICATED = "admin_authenticated";
    public static final String SESSION_USERNAME = "admin_username";
    public static final String SESSION_CSRF_TOKEN = "admin_csrf_token";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final BCryptPasswordEncoder passwordEncoder;
    private final Map<String, LoginAttempt> loginAttempts = new ConcurrentHashMap<>();

    @Value("${admin.username:}")
    private String adminUsername;

    @Value("${admin.password-hash:}")
    private String adminPasswordHash;

    @Value("${admin.login.max-failures:5}")
    private int maxFailures;

    @Value("${admin.login.lock-minutes:10}")
    private long lockMinutes;

    public AdminAuthResponse login(String username, String password, HttpServletRequest request) {
        String attemptKey = attemptKey(username, request);
        rejectIfLocked(attemptKey);

        if (!isValidCredentials(username, password)) {
            recordFailure(attemptKey);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid admin credentials");
        }

        loginAttempts.remove(attemptKey);
        HttpSession previousSession = request.getSession(false);
        if (previousSession != null) {
            previousSession.invalidate();
        }

        HttpSession session = request.getSession(true);
        request.changeSessionId();

        String csrfToken = newCsrfToken();
        session.setAttribute(SESSION_AUTHENTICATED, true);
        session.setAttribute(SESSION_USERNAME, adminUsername);
        session.setAttribute(SESSION_CSRF_TOKEN, csrfToken);

        return new AdminAuthResponse(true, adminUsername, csrfToken);
    }

    public AdminAuthResponse currentAuthentication(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (!isAuthenticated(session)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin login required");
        }
        return new AdminAuthResponse(
                true,
                (String) session.getAttribute(SESSION_USERNAME),
                (String) session.getAttribute(SESSION_CSRF_TOKEN));
    }

    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    public boolean isAuthenticated(HttpSession session) {
        return session != null && Boolean.TRUE.equals(session.getAttribute(SESSION_AUTHENTICATED));
    }

    private boolean isValidCredentials(String username, String password) {
        if (!StringUtils.hasText(adminUsername) || !StringUtils.hasText(adminPasswordHash)) {
            return false;
        }
        return adminUsername.equals(username) && passwordEncoder.matches(password, adminPasswordHash);
    }

    private void rejectIfLocked(String attemptKey) {
        LoginAttempt attempt = loginAttempts.get(attemptKey);
        if (attempt == null || attempt.blockedUntil == null) {
            return;
        }
        if (Instant.now().isBefore(attempt.blockedUntil)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many failed admin login attempts. Try again later.");
        }
        loginAttempts.remove(attemptKey);
    }

    private void recordFailure(String attemptKey) {
        loginAttempts.compute(attemptKey, (key, current) -> {
            LoginAttempt attempt = current == null ? new LoginAttempt() : current;
            attempt.failedCount++;
            if (attempt.failedCount >= maxFailures) {
                attempt.blockedUntil = Instant.now().plus(Duration.ofMinutes(lockMinutes));
            }
            return attempt;
        });
    }

    private String attemptKey(String username, HttpServletRequest request) {
        String normalizedUsername = StringUtils.hasText(username) ? username.trim() : "-";
        return normalizedUsername + "@" + clientIp(request);
    }

    private String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    private String newCsrfToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static class LoginAttempt {
        private int failedCount;
        private Instant blockedUntil;
    }
}
