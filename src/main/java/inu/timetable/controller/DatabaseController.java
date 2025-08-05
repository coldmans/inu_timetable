package inu.timetable.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@CrossOrigin(origins = "https://inu-timetable-front-git-main-jjhs-projects-4d22a2fd.vercel.app")
@RestController
@RequestMapping("/api/database")
public class DatabaseController {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @PostMapping("/add-column")
    public Map<String, Object> addIsRequiredColumn() {
        try {
            jdbcTemplate.execute("ALTER TABLE wishlist_items ADD COLUMN IF NOT EXISTS is_required BOOLEAN DEFAULT false");
            return Map.of("success", true, "message", "is_required column added successfully");
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }
}