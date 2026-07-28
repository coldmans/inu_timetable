package inu.timetable.dto;

import java.util.List;

public record AdminInquiryPageResponse(
        List<AdminInquiryResponse> inquiries,
        int page,
        int size,
        long totalElements,
        int totalPages,
        long unresolvedCount) {
}
