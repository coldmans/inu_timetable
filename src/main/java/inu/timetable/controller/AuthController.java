package inu.timetable.controller;

import inu.timetable.dto.UserResponse;
import inu.timetable.entity.User;
import inu.timetable.security.AuthenticatedUser;
import inu.timetable.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @RequestBody Map<String, Object> request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        try {
            String username = (String) request.get("username");
            String password = (String) request.get("password");
            Integer grade = (Integer) request.get("grade");
            String major = (String) request.get("major");

            User user = authService.register(username, password, grade, major);
            AuthenticatedUser principal = AuthenticatedUser.from(user);
            Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                    principal, null, principal.getAuthorities());
            saveAuthentication(authentication, servletRequest, servletResponse);
            return ResponseEntity.ok(UserResponse.from(user));

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody Map<String, String> request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        try {
            String username = request.get("username");
            String password = request.get("password");

            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(username, password));
            saveAuthentication(authentication, servletRequest, servletResponse);

            AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();
            User user = authService.findById(principal.id());
            return ResponseEntity.ok(UserResponse.from(user));

        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "아이디 또는 비밀번호가 일치하지 않습니다."));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
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
}
