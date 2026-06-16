package inu.timetable.controller;

import inu.timetable.dto.UserResponse;
import inu.timetable.entity.User;
import inu.timetable.enums.UserMajorType;
import inu.timetable.security.AuthenticatedUser;
import inu.timetable.service.AuthService;
import inu.timetable.service.UserActivityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final UserActivityService userActivityService;

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @RequestBody Map<String, Object> request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        String username = (String) request.get("username");
        String password = (String) request.get("password");
        Integer grade = parseInteger(request.get("grade"));
        String major = (String) request.get("major");
        List<AuthService.MajorSelection> majorSelections = parseMajorSelections(request.get("majors"));

        User user = authService.register(username, password, grade, major, majorSelections);
        AuthenticatedUser principal = AuthenticatedUser.from(user);
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());
        saveAuthentication(authentication, servletRequest, servletResponse);
        userActivityService.recordActivity(user.getId());
        return ResponseEntity.ok(UserResponse.from(user));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody Map<String, String> request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        String username = request.get("username");
        String password = request.get("password");

        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(username, password));
        saveAuthentication(authentication, servletRequest, servletResponse);

        AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();
        User user = authService.findById(principal.id());
        userActivityService.recordActivity(user.getId());
        return ResponseEntity.ok(UserResponse.from(user));
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return UserResponse.from(authService.findById(authenticatedUser.id()));
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken csrfToken) {
        return Map.of("token", csrfToken.getToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        new SecurityContextLogoutHandler().logout(
                request,
                response,
                SecurityContextHolder.getContext().getAuthentication());
        return ResponseEntity.ok(Map.of("message", "로그아웃되었습니다."));
    }

    @DeleteMapping("/me")
    public ResponseEntity<?> withdraw(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (authenticatedUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required");
        }

        authService.withdraw(authenticatedUser.id());
        new SecurityContextLogoutHandler().logout(
                request,
                response,
                SecurityContextHolder.getContext().getAuthentication());
        return ResponseEntity.ok(Map.of("message", "회원탈퇴가 완료되었습니다."));
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

    private Integer parseInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return Integer.parseInt(text);
        }
        return null;
    }

    private List<AuthService.MajorSelection> parseMajorSelections(Object rawMajors) {
        if (!(rawMajors instanceof List<?> rawList)) {
            return List.of();
        }

        List<AuthService.MajorSelection> selections = new ArrayList<>();
        for (Object rawItem : rawList) {
            if (!(rawItem instanceof Map<?, ?> item)) {
                continue;
            }

            UserMajorType type = parseMajorType(item.get("type"));
            String department = parseString(item.get("department"));
            if (type != null && StringUtils.hasText(department)) {
                selections.add(new AuthService.MajorSelection(type, department));
            }
        }
        return selections;
    }

    private UserMajorType parseMajorType(Object value) {
        String type = parseString(value);
        if (!StringUtils.hasText(type)) {
            return null;
        }

        return UserMajorType.valueOf(type.trim().toUpperCase(Locale.ROOT));
    }

    private String parseString(Object value) {
        return value instanceof String text ? text : null;
    }
}
