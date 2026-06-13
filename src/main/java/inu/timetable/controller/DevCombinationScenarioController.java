package inu.timetable.controller;

import inu.timetable.dto.UserResponse;
import inu.timetable.security.AuthenticatedUser;
import inu.timetable.service.DevCombinationScenarioService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dev/combination-scenario")
@RequiredArgsConstructor
@Profile({"dev", "local"})
public class DevCombinationScenarioController {

    private final DevCombinationScenarioService scenarioService;
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;

    @PostMapping
    public ResponseEntity<ScenarioResponse> createScenario(
            @RequestBody(required = false) DevCombinationScenarioService.ScenarioRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        DevCombinationScenarioService.ScenarioRequest safeRequest = request == null
                ? new DevCombinationScenarioService.ScenarioRequest(null, null, null, null, null)
                : request;
        DevCombinationScenarioService.ScenarioResult result = scenarioService.prepareScenario(safeRequest);
        AuthenticatedUser principal = AuthenticatedUser.from(result.user());
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());
        saveAuthentication(authentication, servletRequest, servletResponse);

        return ResponseEntity.ok(new ScenarioResponse(
                UserResponse.from(result.user()),
                result.semester(),
                result.requestedWishlistSize(),
                result.slotCount(),
                result.wishlistCount()));
    }

    private void saveAuthentication(
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response) {
        request.getSession(true);
        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    public record ScenarioResponse(
            UserResponse user,
            String semester,
            int requestedWishlistSize,
            int slotCount,
            int wishlistCount) {
    }
}
