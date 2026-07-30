package inu.timetable.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record SubjectImportSelectionRequest(
        @NotEmpty(message = "반영할 과목을 하나 이상 선택해주세요.")
        List<String> courseCodes) {
}
