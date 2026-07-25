package inu.timetable.service;

import inu.timetable.dto.UserNotificationResponse;
import inu.timetable.repository.UserNotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserNotificationService {

    private final UserNotificationRepository userNotificationRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<UserNotificationResponse> getUnreadNotifications(Long userId) {
        return userNotificationRepository.findAllByUserIdAndReadAtIsNullOrderByCreatedAtAscIdAsc(userId)
                .stream()
                .map(UserNotificationResponse::fromEntity)
                .toList();
    }

    @Transactional
    public int markAllRead(Long userId) {
        return userNotificationRepository.markAllRead(userId, LocalDateTime.now(clock));
    }
}
