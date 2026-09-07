package inu.timetable.controller;

import inu.timetable.dto.AdminAuthResponse;
import inu.timetable.dto.AdminLoginRequest;
import inu.timetable.service.AdminAuthService;
import inu.timetable.service.SessionMigrationBridgeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
    private final SessionMigrationBridgeService sessionMigrationBridgeService;

    @PostMapping("/login")
    public AdminAuthResponse login(
            @Valid @RequestBody AdminLoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        AdminAuthResponse response = adminAuthService.login(
                request.username(), request.password(), servletRequest);
        if (!response.passwordChangeRequired()) {
            sessionMigrationBridgeService.rotateAdmin(servletRequest, servletResponse);
        }
        return response;
    }

    @GetMapping("/me")
    public AdminAuthResponse me(HttpServletRequest request) {
        return adminAuthService.currentAuthentication(request);
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(
            HttpServletRequest request,
            HttpServletResponse response) {
        sessionMigrationBridgeService.revoke(request, response);
        adminAuthService.logout(request);
        return Map.of("loggedOut", true);
    }
}
