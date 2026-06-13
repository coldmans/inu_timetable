package inu.timetable.controller;

import inu.timetable.dto.UserResponse;
import inu.timetable.security.AuthenticatedUser;
import inu.timetable.service.DevSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dev")
@RequiredArgsConstructor
@Profile({"dev", "local"})
public class DevSessionController {

    private static final String DEFAULT_SEMESTER = "2026-1";

    private final DevSessionService devSessionService;
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;

    @PostMapping("/session")
    public ResponseEntity<DevSessionResponse> createSession(
            @RequestBody(required = false) DevSessionRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        DevSessionRequest safeRequest = request != null ? request : new DevSessionRequest(null, null, null);
        String semester = StringUtils.hasText(safeRequest.semester()) ? safeRequest.semester() : DEFAULT_SEMESTER;
        boolean seedWishlist = safeRequest.seedWishlist() == null || safeRequest.seedWishlist();
        boolean reset = Boolean.TRUE.equals(safeRequest.reset());

        DevSessionService.DevSessionResult result = devSessionService.prepareSession(semester, seedWishlist, reset);
        AuthenticatedUser principal = AuthenticatedUser.from(result.user());
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());
        saveAuthentication(authentication, servletRequest, servletResponse);

        return ResponseEntity.ok(new DevSessionResponse(
                UserResponse.from(result.user()),
                result.semester(),
                result.wishlistCount(),
                result.timetableCount(),
                result.seededWishlistCount()));
    }

    private void saveAuthentication(
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response) {
        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    public record DevSessionRequest(String semester, Boolean seedWishlist, Boolean reset) {
    }

    public record DevSessionResponse(
            UserResponse user,
            String semester,
            int wishlistCount,
            int timetableCount,
            int seededWishlistCount) {
    }
}
