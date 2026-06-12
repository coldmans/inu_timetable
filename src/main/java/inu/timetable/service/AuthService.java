package inu.timetable.service;

import inu.timetable.entity.User;
import inu.timetable.repository.UserRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            UserRepository userRepository,
            @Qualifier("userPasswordEncoder") PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User register(String username, String password, Integer grade, String major) {
        validateCredentials(username, password);
        String normalizedUsername = username.trim();
        if (userRepository.existsByUsername(normalizedUsername)) {
            throw new RuntimeException("이미 사용 중인 아이디입니다. 다른 아이디를 입력해주세요.");
        }

        User user = User.builder()
            .username(normalizedUsername)
            .password(passwordEncoder.encode(password))
            .grade(grade)
            .major(major)
            .build();

        return userRepository.save(user);
    }

    public User findById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
    }

    private void validateCredentials(String username, String password) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new RuntimeException("아이디와 비밀번호를 입력해주세요.");
        }
    }
}
