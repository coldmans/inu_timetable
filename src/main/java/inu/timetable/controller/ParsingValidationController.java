package inu.timetable.controller;

import inu.timetable.service.ParsingValidationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 파싱 결과 검증 컨트롤러
 * DB 데이터와 파싱 결과를 비교하여 정확도 검증
 */
@RestController
@RequestMapping("/api/validation")
@RequiredArgsConstructor
@Tag(name = "파싱 검증", description = "파싱 결과와 DB 데이터 비교 검증 API")
public class ParsingValidationController {

    private final ParsingValidationService validationService;

    @PostMapping(value = "/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "PDF 파싱 결과 검증",
        description = "PDF 파일을 파싱하여 DB에 저장된 데이터와 비교합니다. " +
                     "누락된 과목, 중복된 과목, 필드 불일치 등을 리포트로 반환합니다."
    )
    public ResponseEntity<?> validatePdfParsing(
            @Parameter(description = "검증할 PDF 파일", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "학기 (예: 2025-1) - 선택사항")
            @RequestParam(value = "semester", required = false) String semester) {
        try {
            Map<String, Object> report = validationService.validatePdfParsing(file, semester);
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "PDF 파싱 검증 실패",
                "message", e.getMessage()
            ));
        }
    }

    @PostMapping(value = "/excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "Excel 파싱 결과 검증",
        description = "Excel 파일을 파싱하여 DB에 저장된 데이터와 비교합니다. " +
                     "누락된 과목, 중복된 과목, 필드 불일치 등을 리포트로 반환합니다."
    )
    public ResponseEntity<?> validateExcelParsing(
            @Parameter(description = "검증할 Excel 파일 (.xlsx)", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "학기 (예: 2025-1) - 선택사항")
            @RequestParam(value = "semester", required = false) String semester) {
        try {
            Map<String, Object> report = validationService.validateExcelParsing(file, semester);
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Excel 파싱 검증 실패",
                "message", e.getMessage()
            ));
        }
    }
}
