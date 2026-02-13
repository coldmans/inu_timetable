package inu.timetable.service;

import inu.timetable.entity.User;
import inu.timetable.repository.UserRepository;
import inu.timetable.repository.UserTimetableRepository;
import inu.timetable.repository.WishlistRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.util.Optional;

@Service
public class AuthService {
    
    private final UserRepository userRepository;
    private final UserTimetableRepository userTimetableRepository;
    private final WishlistRepository wishlistRepository;

    @Autowired
    public AuthService(UserRepository userRepository,
                       UserTimetableRepository userTimetableRepository,
                       WishlistRepository wishlistRepository) {
        this.userRepository = userRepository;
        this.userTimetableRepository = userTimetableRepository;
        this.wishlistRepository = wishlistRepository;
    }
    
    public User register(String username, String password, Integer grade, String major) {
        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("이미 사용 중인 아이디입니다. 다른 아이디를 입력해주세요.");
        }
        
        String hashedPassword = hashPassword(password);
        
        User user = User.builder()
            .username(username)
            .password(hashedPassword)
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
    
    @Transactional
    public void withdraw(Long userId, String password) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        String hashedPassword = hashPassword(password);
        if (!user.getPassword().equals(hashedPassword)) {
            throw new RuntimeException("비밀번호가 일치하지 않습니다.");
        }

        // 연관 데이터 정리 후 사용자 삭제
        wishlistRepository.deleteByUserId(userId);
        userTimetableRepository.deleteByUserId(userId);
        userRepository.delete(user);
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