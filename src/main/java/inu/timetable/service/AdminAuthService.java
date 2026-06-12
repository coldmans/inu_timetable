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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AdminAuthService {

    public static final String SESSION_AUTHENTICATED = "admin_authenticated";
    public static final String SESSION_USERNAME = "admin_username";

    private final BCryptPasswordEncoder passwordEncoder;
    private final Map<String, LoginAttempt> loginAttempts = new ConcurrentHashMap<>();

    @Value("${admin.username:}")
    private String adminUsername;

    @Value("${admin.password-hash:}")
    private String adminPasswordHash;

    @Value("${admin.password:}")
    private String legacyAdminPassword;

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

        session.setAttribute(SESSION_AUTHENTICATED, true);
        session.setAttribute(SESSION_USERNAME, adminUsername);

        return new AdminAuthResponse(true, adminUsername);
    }

    public AdminAuthResponse currentAuthentication(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (!isAuthenticated(session)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin login required");
        }
        return new AdminAuthResponse(
                true,
                (String) session.getAttribute(SESSION_USERNAME));
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
        if (!StringUtils.hasText(adminUsername)) {
            return false;
        }
        if (!adminUsername.equals(username)) {
            return false;
        }
        if (StringUtils.hasText(adminPasswordHash)) {
            return passwordEncoder.matches(password, adminPasswordHash);
        }
        return StringUtils.hasText(legacyAdminPassword) && constantTimeEquals(password, legacyAdminPassword);
    }

    private boolean constantTimeEquals(String actual, String expected) {
        byte[] actualBytes = String.valueOf(actual).getBytes(StandardCharsets.UTF_8);
        byte[] expectedBytes = String.valueOf(expected).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(actualBytes, expectedBytes);
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

    private static class LoginAttempt {
        private int failedCount;
        private Instant blockedUntil;
    }
}
