package inu.timetable.controller;

import inu.timetable.dto.UserNotificationResponse;
import inu.timetable.security.AuthenticatedUser;
import inu.timetable.service.UserNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 로그인 유저 본인의 1회성 알림 조회/읽음 처리 API.
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final UserNotificationService userNotificationService;

    @GetMapping("/unread")
    public ResponseEntity<List<UserNotificationResponse>> getUnreadNotifications(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return ResponseEntity.ok(userNotificationService.getUnreadNotifications(authenticatedUser.id()));
    }

    @PostMapping("/read")
    public ResponseEntity<Map<String, Integer>> markAllRead(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        int readCount = userNotificationService.markAllRead(authenticatedUser.id());
        return ResponseEntity.ok(Map.of("readCount", readCount));
    }
}
