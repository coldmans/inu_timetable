package inu.timetable.service;

import inu.timetable.entity.User;
import inu.timetable.repository.UserRepository;
import inu.timetable.repository.UserTimetableRepository;
import inu.timetable.repository.WishlistRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final UserTimetableRepository userTimetableRepository;
    private final WishlistRepository wishlistRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

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

        User user = User.builder()
            .username(username)
            .password(passwordEncoder.encode(password))
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
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("비밀번호가 일치하지 않습니다.");
        }

        return user;
    }

    @Transactional
    public void withdraw(String username, String password) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("비밀번호가 일치하지 않습니다.");
        }

        wishlistRepository.deleteByUserId(user.getId());
        userTimetableRepository.deleteByUserId(user.getId());
        userRepository.delete(user);
    }
}
