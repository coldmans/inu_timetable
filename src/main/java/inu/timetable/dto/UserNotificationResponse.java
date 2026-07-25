package inu.timetable.dto;

import inu.timetable.entity.UserNotification;

import java.time.LocalDateTime;

public record UserNotificationResponse(
        Long id,
        String message,
        LocalDateTime createdAt) {

    public static UserNotificationResponse fromEntity(UserNotification notification) {
        return new UserNotificationResponse(
                notification.getId(),
                notification.getMessage(),
                notification.getCreatedAt());
    }
}
