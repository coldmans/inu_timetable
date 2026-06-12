package inu.timetable.controller;

import inu.timetable.service.AdminAccessGuard;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin/api/database")
@RequiredArgsConstructor
public class DatabaseController {

    private final JdbcTemplate jdbcTemplate;
    private final AdminAccessGuard adminAccessGuard;

    @PostMapping("/add-column")
    public Map<String, Object> addIsRequiredColumn(
            HttpServletRequest servletRequest) {
        adminAccessGuard.requireAuthenticated(servletRequest);
        try {
            jdbcTemplate.execute("ALTER TABLE wishlist_items ADD COLUMN IF NOT EXISTS is_required BOOLEAN DEFAULT false");
            return Map.of("success", true, "message", "is_required column added successfully");
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }
}
