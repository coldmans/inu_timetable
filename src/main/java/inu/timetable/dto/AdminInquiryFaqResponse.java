package inu.timetable.dto;

import java.time.LocalDateTime;

public record AdminInquiryFaqResponse(
        Long id,
        String question,
        String answer,
        int sortOrder,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
