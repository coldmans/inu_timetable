package inu.timetable.service;

import inu.timetable.entity.User;
import inu.timetable.repository.UserRepository;
import inu.timetable.security.LegacySha256DelegatingPasswordEncoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

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
    }
}
