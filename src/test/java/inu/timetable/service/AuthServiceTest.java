package inu.timetable.service;

import inu.timetable.entity.User;
import inu.timetable.enums.UserMajorType;
import inu.timetable.enums.UserStatus;
import inu.timetable.repository.UserRepository;
import inu.timetable.security.LegacySha256DelegatingPasswordEncoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = new LegacySha256DelegatingPasswordEncoder(new BCryptPasswordEncoder(4));
        authService = new AuthService(userRepository, passwordEncoder);
    }

    @Test
    void registerStoresBcryptPassword() {
        when(userRepository.existsByUsername("student")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.register("student", "password123", 2, "컴퓨터공학부");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(passwordEncoder.matches("password123", savedUser.getPassword())).isTrue();
        assertThat(savedUser.getMajor()).isEqualTo("컴퓨터공학부");
        assertThat(savedUser.getUserMajors())
                .extracting(userMajor -> userMajor.getType() + ":" + userMajor.getDepartment())
                .containsExactly("PRIMARY:컴퓨터공학부");
    }

    @Test
    void registerStoresDoubleAndMinorMajors() {
        when(userRepository.existsByUsername("student")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.register("student", "password123", 2, "컴퓨터공학부", List.of(
                new AuthService.MajorSelection(UserMajorType.DOUBLE, "데이터과학과"),
                new AuthService.MajorSelection(UserMajorType.MINOR, "경영학부")
        ));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getMajor()).isEqualTo("컴퓨터공학부");
        assertThat(savedUser.getUserMajors())
                .extracting(userMajor -> userMajor.getType() + ":" + userMajor.getDepartment())
                .containsExactly(
                        "PRIMARY:컴퓨터공학부",
                        "DOUBLE:데이터과학과",
                        "MINOR:경영학부"
                );
    }

    @Test
    void withdrawAnonymizesLoginFieldsAndKeepsAggregateFields() {
        User user = User.builder()
                .id(10L)
                .username("student")
                .password(passwordEncoder.encode("password123"))
                .nickname("수강왕")
                .grade(3)
                .major("컴퓨터공학부")
                .build();
        when(userRepository.findByIdAndStatus(10L, UserStatus.ACTIVE)).thenReturn(java.util.Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.withdraw(10L);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User withdrawnUser = userCaptor.getValue();
        assertThat(withdrawnUser.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(withdrawnUser.getDeletedAt()).isNotNull();
        assertThat(withdrawnUser.getUsername()).startsWith("withdrawn_user_10_");
        assertThat(withdrawnUser.getNickname()).isNull();
        assertThat(passwordEncoder.matches("password123", withdrawnUser.getPassword())).isFalse();
        assertThat(withdrawnUser.getGrade()).isEqualTo(3);
        assertThat(withdrawnUser.getMajor()).isEqualTo("컴퓨터공학부");
    }
}
