package inu.timetable.service;

import inu.timetable.security.AuthenticatedUser;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

@Service
public class SessionMigrationBridgeService {

    private final SessionMigrationTokenService tokenService;
    private final Mode mode;
    private final String cookieName;
    private final Duration ttl;
    private final boolean cookieSecure;
    private final String cookieSameSite;
    private final String cookiePath;

    public SessionMigrationBridgeService(
            SessionMigrationTokenService tokenService,
            @Value("${session.bridge.mode:off}") String mode,
            @Value("${session.bridge.cookie-name:INU_SESSION_BRIDGE}") String cookieName,
            @Value("${session.bridge.ttl:30m}") Duration ttl,
            @Value("${server.servlet.session.cookie.secure:false}") boolean cookieSecure,
            @Value("${server.servlet.session.cookie.same-site:lax}") String cookieSameSite,
            @Value("${server.servlet.session.cookie.path:/}") String cookiePath) {
        this.tokenService = tokenService;
        this.mode = Mode.valueOf(mode.trim().toUpperCase(Locale.ROOT));
        this.cookieName = cookieName;
        this.ttl = ttl;
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = cookieSameSite;
        this.cookiePath = cookiePath;
    }

    public boolean isIssueMode() {
        return mode == Mode.ISSUE;
    }

    public boolean isConsumeMode() {
        return mode == Mode.CONSUME;
    }

    public void issueForCurrentSession(
            HttpServletRequest request,
            HttpServletResponse response) {
        if (!isIssueMode() || findCookie(request).isPresent()) {
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            issueUser(user.id(), response);
            return;
        }

        HttpSession session = request.getSession(false);
        if (session != null
                && Boolean.TRUE.equals(session.getAttribute(AdminAuthService.SESSION_AUTHENTICATED))) {
            String username = (String) session.getAttribute(AdminAuthService.SESSION_USERNAME);
            issueAdmin(username, response);
        }
    }

    public void issueUser(Long userId, HttpServletResponse response) {
        if (isIssueMode()) {
            addCookie(response, tokenService.issue(SessionMigrationPrincipal.user(userId), ttl), ttl);
        }
    }

    public void rotateUser(
            Long userId,
            HttpServletRequest request,
            HttpServletResponse response) {
        revoke(request, response);
        issueUser(userId, response);
    }

    public void issueAdmin(String username, HttpServletResponse response) {
        if (isIssueMode()) {
            addCookie(response, tokenService.issue(SessionMigrationPrincipal.admin(username), ttl), ttl);
        }
    }

    public void rotateAdmin(
            String username,
            HttpServletRequest request,
            HttpServletResponse response) {
        revoke(request, response);
        issueAdmin(username, response);
    }

    public Optional<SessionMigrationPrincipal> consume(HttpServletRequest request) {
        if (!isConsumeMode()) {
            return Optional.empty();
        }
        return findCookie(request).flatMap(cookie -> tokenService.consume(cookie.getValue()));
    }

    public boolean hasBridgeCookie(HttpServletRequest request) {
        return findCookie(request).isPresent();
    }

    public void clearCookie(HttpServletResponse response) {
        addCookie(response, "", Duration.ZERO);
    }

    public void revoke(HttpServletRequest request, HttpServletResponse response) {
        if (mode == Mode.OFF) {
            return;
        }
        findCookie(request).ifPresent(cookie -> {
            tokenService.revoke(cookie.getValue());
            clearCookie(response);
        });
    }

    private Optional<Cookie> findCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> cookieName.equals(cookie.getName()))
                .findFirst();
    }

    private void addCookie(HttpServletResponse response, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(cookieName, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path(cookiePath)
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    enum Mode {
        OFF,
        ISSUE,
        CONSUME
    }
}
