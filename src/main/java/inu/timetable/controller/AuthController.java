package inu.timetable.controller;

import inu.timetable.entity.User;
import inu.timetable.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    
    private final AuthService authService;
    
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, Object> request) {
        try {
            String username = (String) request.get("username");
            String password = (String) request.get("password");
            String nickname = (String) request.get("nickname");
            Integer grade = (Integer) request.get("grade");
            String major = (String) request.get("major");
            
            User user = authService.register(username, password, nickname, grade, major);
            
            // 비밀번호 제거 후 응답
            user.setPassword(null);
            return ResponseEntity.ok(user);
            
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        try {
            String username = request.get("username");
            String password = request.get("password");
            
            User user = authService.login(username, password);
            
            // 비밀번호 제거 후 응답
            user.setPassword(null);
            return ResponseEntity.ok(user);
            
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}