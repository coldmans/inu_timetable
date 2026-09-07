package inu.timetable.security;

import inu.timetable.entity.User;
import inu.timetable.service.AdminAuthService;
import inu.timetable.service.AuthService;
import inu.timetable.service.SessionMigrationBridgeService;
import inu.timetable.service.SessionMigrationPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class SessionMigrationBridgeFilter extends OncePerRequestFilter {

    private final SessionMigrationBridgeService bridgeService;
    private final AdminAuthService adminAuthService;
    private final AuthService authService;
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (bridgeService.isConsumeMode()) {
            recoverSession(request, response);
        } else if (bridgeService.isIssueMode() && !changesAuthentication(request)) {
            bridgeService.issueForCurrentSession(request, response);
        }
        filterChain.doFilter(request, response);
    }

    private boolean changesAuthentication(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        return ("POST".equals(method)
                    && ("/api/auth/login".equals(path)
                        || "/api/auth/register".equals(path)
                        || "/api/auth/logout".equals(path)
                        || "/admin/api/auth/login".equals(path)
                        || "/admin/api/auth/logout".equals(path)
                        || "/admin/login".equals(path)
                        || "/admin/logout".equals(path)
                        || "/admin/account".equals(path)))
                || ("DELETE".equals(method) && "/api/auth/me".equals(path));
    }

    private void recoverSession(HttpServletRequest request, HttpServletResponse response) {
        if (alreadyAuthenticated(request) || !bridgeService.hasBridgeCookie(request)) {
            return;
        }

        Optional<SessionMigrationPrincipal> migratedPrincipal;
        try {
            migratedPrincipal = bridgeService.consume(request);
        } catch (RuntimeException exception) {
            log.warn("Session migration token lookup failed", exception);
            return;
        }

        if (migratedPrincipal.isEmpty()) {
            bridgeService.clearCookie(response);
            return;
        }

        try {
            SessionMigrationPrincipal principal = migratedPrincipal.get();
            if (principal.type() == SessionMigrationPrincipal.PrincipalType.USER) {
                recoverUser(principal.userId(), request, response);
            } else {
                recoverAdmin(
                        principal.adminUsername(),
                        principal.adminCredentialVersion(),
                        request);
            }
            bridgeService.clearCookie(response);
        } catch (RuntimeException exception) {
            bridgeService.clearCookie(response);
            log.warn("Session migration principal recovery failed", exception);
        }
    }

    private void recoverUser(
            Long userId,
            HttpServletRequest request,
            HttpServletResponse response) {
        User user = authService.findById(userId);
        AuthenticatedUser principal = AuthenticatedUser.forSession(user);
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());

        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    private void recoverAdmin(
            String username,
            Long credentialVersion,
            HttpServletRequest request) {
        if (!adminAuthService.restoreMigratedSession(
                username,
                credentialVersion,
                request)) {
            throw new IllegalStateException("Admin migration credential is stale");
        }
    }

    private boolean alreadyAuthenticated(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AuthenticatedUser) {
            return true;
        }

        HttpSession session = request.getSession(false);
        return session != null
                && Boolean.TRUE.equals(session.getAttribute(AdminAuthService.SESSION_AUTHENTICATED));
    }
}
