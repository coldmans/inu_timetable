package inu.timetable.dto;

import inu.timetable.entity.UserInquiry;

import java.time.LocalDateTime;

public record AdminInquiryResponse(
        Long id,
        Long userId,
        String username,
        String content,
        String contact,
        LocalDateTime createdAt,
        LocalDateTime resolvedAt) {

    public static AdminInquiryResponse fromEntity(UserInquiry inquiry, String username) {
        return new AdminInquiryResponse(
                inquiry.getId(),
                inquiry.getUserId(),
                username,
                inquiry.getContent(),
                inquiry.getContact(),
                inquiry.getCreatedAt(),
                inquiry.getResolvedAt());
    }
}
