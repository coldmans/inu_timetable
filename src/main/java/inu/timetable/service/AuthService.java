package inu.timetable.service;

import inu.timetable.entity.User;
import inu.timetable.entity.UserMajor;
import inu.timetable.enums.UserMajorType;
import inu.timetable.enums.UserStatus;
import inu.timetable.exception.ApiException;
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

    @Transactional
    public User register(
            String username,
            String password,
            Integer grade,
            String major,
            List<MajorSelection> majorSelections) {
        validateCredentials(username, password);
        String normalizedUsername = username.trim();
        if (userRepository.existsByUsername(normalizedUsername)) {
            throw ApiException.conflict("이미 사용 중인 아이디입니다. 다른 아이디를 입력해주세요.");
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
                .orElseThrow(() -> ApiException.notFound("사용자를 찾을 수 없습니다."));
    }

    @Transactional
    public User updateProfile(
            Long userId,
            Integer grade,
            String major,
            List<MajorSelection> majorSelections) {
        User user = userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)
                .orElseThrow(() -> ApiException.notFound("사용자를 찾을 수 없습니다."));

        if (grade != null) {
            validateGrade(grade);
            user.setGrade(grade);
        }

        boolean shouldUpdateMajors = StringUtils.hasText(major)
                || (majorSelections != null && !majorSelections.isEmpty());
        if (shouldUpdateMajors) {
            List<MajorSelection> normalizedMajors = normalizeMajorSelections(major, majorSelections);
            String primaryMajor = normalizedMajors.stream()
                    .filter(selection -> selection.type() == UserMajorType.PRIMARY)
                    .map(MajorSelection::department)
                    .findFirst()
                    .orElseThrow(() -> ApiException.badRequest("전공을 선택해주세요."));

            user.setMajor(primaryMajor);
            replaceUserMajors(user, normalizedMajors);
        }

        return user;
    }

    @Transactional
    public User withdraw(Long userId) {
        User user = userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)
                .orElseThrow(() -> ApiException.notFound("사용자를 찾을 수 없습니다."));
        String anonymizedUsername = "withdrawn_user_%d_%s".formatted(
                user.getId(),
                UUID.randomUUID());
        String anonymizedPassword = passwordEncoder.encode(UUID.randomUUID().toString());
        user.withdraw(anonymizedUsername, anonymizedPassword, LocalDateTime.now());
        return userRepository.save(user);
    }

    private static final int USERNAME_MAX_LENGTH = 50;
    private static final int PASSWORD_MIN_LENGTH = 8;
    private static final int PASSWORD_MAX_LENGTH = 72; // bcrypt 입력 바이트 한계 고려

    private void validateCredentials(String username, String password) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw ApiException.badRequest("아이디와 비밀번호를 입력해주세요.");
        }
        if (username.trim().length() > USERNAME_MAX_LENGTH) {
            throw ApiException.badRequest("아이디는 " + USERNAME_MAX_LENGTH + "자 이하로 입력해주세요.");
        }
        if (password.length() < PASSWORD_MIN_LENGTH || password.length() > PASSWORD_MAX_LENGTH) {
            throw ApiException.badRequest(
                    "비밀번호는 " + PASSWORD_MIN_LENGTH + "자 이상 " + PASSWORD_MAX_LENGTH + "자 이하로 입력해주세요.");
        }
    }

    private void validateGrade(Integer grade) {
        if (grade < 1 || grade > 4) {
            throw ApiException.badRequest("학년은 1~4학년 중에서 선택해주세요.");
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

    private void replaceUserMajors(User user, List<MajorSelection> normalizedMajors) {
        Map<String, MajorSelection> selectionsByDepartment = new LinkedHashMap<>();
        for (MajorSelection selection : normalizedMajors) {
            selectionsByDepartment.put(selection.department(), selection);
        }

        user.getUserMajors().removeIf(userMajor -> !selectionsByDepartment.containsKey(userMajor.getDepartment()));
        for (MajorSelection selection : selectionsByDepartment.values()) {
            UserMajor existingUserMajor = user.getUserMajors().stream()
                    .filter(userMajor -> userMajor.getDepartment().equals(selection.department()))
                    .findFirst()
                    .orElse(null);

            if (existingUserMajor != null) {
                existingUserMajor.setType(selection.type());
                continue;
            }

            user.addUserMajor(UserMajor.builder()
                    .type(selection.type())
                    .department(selection.department())
                    .build());
        }
    }

    private String selectionKey(MajorSelection selection) {
        return selection.department();
    }

    public record MajorSelection(UserMajorType type, String department) {
    }
}
