package inu.timetable.dto;

public record InquiryFaqUpsertRequest(
        String question,
        String answer,
        Integer sortOrder,
        Boolean active) {
}
