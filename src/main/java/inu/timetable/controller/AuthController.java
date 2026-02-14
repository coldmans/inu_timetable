package inu.timetable.controller;

import inu.timetable.dto.UserResponse;
import inu.timetable.dto.auth.LoginRequest;
import inu.timetable.dto.auth.RegisterRequest;
import inu.timetable.dto.auth.WithdrawRequest;
import inu.timetable.entity.User;
import inu.timetable.service.AuthService;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    
    private final AuthService authService;
    
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(request.username(), request.password(), request.grade(), request.major());
        return ResponseEntity.ok(UserResponse.from(user));
    }
    
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        User user = authService.login(request.username(), request.password());
        return ResponseEntity.ok(UserResponse.from(user));
    }

    @DeleteMapping("/withdraw")
    public ResponseEntity<?> withdraw(@Valid @RequestBody WithdrawRequest request) {
        authService.withdraw(request.userId(), request.password());
        return ResponseEntity.ok(Map.of("message", "회원탈퇴가 완료되었습니다."));
    }
}