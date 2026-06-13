package inu.timetable.service;

import inu.timetable.entity.User;
import inu.timetable.entity.UserMajor;
import inu.timetable.enums.UserMajorType;
import inu.timetable.enums.UserStatus;
import inu.timetable.repository.UserRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
        return register(username, password, grade, major, List.of());
    }

    public User register(
            String username,
            String password,
            Integer grade,
            String major,
            List<MajorSelection> majorSelections) {
        validateCredentials(username, password);
        String normalizedUsername = username.trim();
        if (userRepository.existsByUsername(normalizedUsername)) {
            throw new RuntimeException("이미 사용 중인 아이디입니다. 다른 아이디를 입력해주세요.");
        }

        List<MajorSelection> normalizedMajors = normalizeMajorSelections(major, majorSelections);
        String primaryMajor = normalizedMajors.stream()
                .filter(selection -> selection.type() == UserMajorType.PRIMARY)
                .map(MajorSelection::department)
                .findFirst()
                .orElseGet(() -> StringUtils.hasText(major) ? major.trim() : null);

        User user = User.builder()
            .username(normalizedUsername)
            .password(passwordEncoder.encode(password))
            .grade(grade)
            .major(primaryMajor)
            .build();
        user.replaceUserMajors(normalizedMajors.stream()
                .map(selection -> UserMajor.builder()
                        .type(selection.type())
                        .department(selection.department())
                        .build())
                .toList());

        return userRepository.save(user);
    }

    public User findById(Long userId) {
        return userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
    }

    @Transactional
    public User withdraw(Long userId) {
        User user = userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
        String anonymizedUsername = "withdrawn_user_%d_%s".formatted(
                user.getId(),
                UUID.randomUUID());
        String anonymizedPassword = passwordEncoder.encode(UUID.randomUUID().toString());
        user.withdraw(anonymizedUsername, anonymizedPassword, LocalDateTime.now());
        return userRepository.save(user);
    }

    private void validateCredentials(String username, String password) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new RuntimeException("아이디와 비밀번호를 입력해주세요.");
        }
    }

    private List<MajorSelection> normalizeMajorSelections(String major, List<MajorSelection> majorSelections) {
        Map<String, MajorSelection> uniqueSelections = new LinkedHashMap<>();
        if (StringUtils.hasText(major)) {
            MajorSelection primary = new MajorSelection(UserMajorType.PRIMARY, major.trim());
            uniqueSelections.put(selectionKey(primary), primary);
        }

        for (MajorSelection selection : majorSelections == null ? List.<MajorSelection>of() : majorSelections) {
            if (selection == null || selection.type() == null || !StringUtils.hasText(selection.department())) {
                continue;
            }

            MajorSelection normalized = new MajorSelection(selection.type(), selection.department().trim());
            uniqueSelections.putIfAbsent(selectionKey(normalized), normalized);
        }

        return new ArrayList<>(uniqueSelections.values());
    }

    private String selectionKey(MajorSelection selection) {
        return selection.department();
    }

    public record MajorSelection(UserMajorType type, String department) {
    }
}
