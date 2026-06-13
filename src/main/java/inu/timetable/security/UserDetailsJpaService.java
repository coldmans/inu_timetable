package inu.timetable.security;

import inu.timetable.entity.User;
import inu.timetable.enums.UserStatus;
import inu.timetable.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsPasswordService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserDetailsJpaService implements UserDetailsService, UserDetailsPasswordService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) {
        return userRepository.findByUsernameAndStatus(username, UserStatus.ACTIVE)
                .map(AuthenticatedUser::from)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));
    }

    @Override
    @Transactional
    public UserDetails updatePassword(UserDetails userDetails, String newPassword) {
        if (!(userDetails instanceof AuthenticatedUser authenticatedUser)) {
            throw new UsernameNotFoundException("사용자를 찾을 수 없습니다.");
        }

        User user = userRepository.findByIdAndStatus(authenticatedUser.id(), UserStatus.ACTIVE)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));
        user.setPassword(newPassword);
        return AuthenticatedUser.from(userRepository.save(user));
    }
}
