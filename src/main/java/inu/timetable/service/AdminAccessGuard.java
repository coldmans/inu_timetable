package inu.timetable.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AdminAccessGuard {

    public static final String ADMIN_PASSWORD_HEADER = "X-Admin-Password";

    @Value("${admin.password:}")
    private String adminPassword;

    public void requireAdminPassword(String providedPassword) {
        if (!StringUtils.hasText(adminPassword) || !adminPassword.equals(providedPassword)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
    }
}
