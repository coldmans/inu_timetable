package inu.timetable.controller;

import inu.timetable.service.AdminAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/database")
@RequiredArgsConstructor
public class DatabaseController {

    private final JdbcTemplate jdbcTemplate;
    private final AdminAccessGuard adminAccessGuard;

    @PostMapping("/add-column")
    public Map<String, Object> addIsRequiredColumn(
            @RequestHeader(value = AdminAccessGuard.ADMIN_PASSWORD_HEADER, required = false) String adminPassword) {
        adminAccessGuard.requireAdminPassword(adminPassword);
        try {
            jdbcTemplate.execute("ALTER TABLE wishlist_items ADD COLUMN IF NOT EXISTS is_required BOOLEAN DEFAULT false");
            return Map.of("success", true, "message", "is_required column added successfully");
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }
}
