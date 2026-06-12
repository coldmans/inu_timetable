package inu.timetable.controller;

import inu.timetable.dto.AdminAuthResponse;
import inu.timetable.dto.AdminLoginRequest;
import inu.timetable.service.AdminAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin/api/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    @PostMapping("/login")
    public AdminAuthResponse login(
            @Valid @RequestBody AdminLoginRequest request,
            HttpServletRequest servletRequest) {
        return adminAuthService.login(request.username(), request.password(), servletRequest);
    }

    @GetMapping("/me")
    public AdminAuthResponse me(HttpServletRequest request) {
        return adminAuthService.currentAuthentication(request);
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest request) {
        adminAuthService.logout(request);
        return Map.of("loggedOut", true);
    }
}
