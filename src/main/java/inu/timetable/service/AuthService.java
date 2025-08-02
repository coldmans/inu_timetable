package inu.timetable.service;

import inu.timetable.entity.User;
import inu.timetable.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.util.Optional;

@Service
public class AuthService {
    
    private final UserRepository userRepository;
    
    @Autowired
    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    
    public User register(String username, String password, String nickname, Integer grade, String major) {
        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("이미 존재하는 사용자명입니다.");
        }
        
        String hashedPassword = hashPassword(password);
        
        User user = User.builder()
            .username(username)
            .password(hashedPassword)
            .nickname(nickname)
            .grade(grade)
            .major(major)
            .build();
            
        return userRepository.save(user);
    }
    
    public User login(String username, String password) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            throw new RuntimeException("사용자를 찾을 수 없습니다.");
        }
        
        User user = userOpt.get();
        String hashedPassword = hashPassword(password);
        
        if (!user.getPassword().equals(hashedPassword)) {
            throw new RuntimeException("비밀번호가 일치하지 않습니다.");
        }
        
        return user;
    }
    
    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashedBytes = md.digest(password.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hashedBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("비밀번호 해시 생성 실패", e);
        }
    }
}