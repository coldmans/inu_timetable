package inu.timetable.controller;

import inu.timetable.service.ExcelParseService;
import inu.timetable.service.AdminAccessGuard;
import inu.timetable.service.PdfParseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/admin/api")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "파일 업로드", description = "PDF 및 Excel 파일 업로드 및 파싱 API")
public class PdfParseController {

    private final PdfParseService pdfParseService;
    private final ExcelParseService excelParseService;
    private final AdminAccessGuard adminAccessGuard;

    @PostMapping(value = "/pdf/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "PDF 파일 업로드", description = "수강편람 PDF 파일을 업로드하여 과목 데이터를 파싱하고 저장합니다. Gemini AI를 사용하여 파싱합니다.")
    public ResponseEntity<String> uploadAndParsePdf(
            HttpServletRequest servletRequest,
            @Parameter(description = "수강편람 PDF 파일", required = true) @RequestParam("file") MultipartFile file) {
        adminAccessGuard.requireAuthenticated(servletRequest);
        try {
            int savedCount = pdfParseService.parseAndSaveSubjectsIncremental(file);
            return ResponseEntity.ok("PDF 파싱 완료. " + savedCount + "개의 과목이 저장되었습니다.");
        } catch (Exception e) {
            // 내부/외부 API 오류 메시지를 응답으로 노출하지 않고 로그로만 남긴다.
            log.error("PDF 파싱 실패", e);
            return ResponseEntity.badRequest().body("PDF 파싱에 실패했습니다. 파일 형식과 내용을 확인해주세요.");
        }
    }

    @PostMapping(value = "/excel/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Excel 파일 업로드", description = "종합강의시간표 Excel 파일(.xlsx)을 업로드하여 과목 데이터를 파싱하고 저장합니다. 기존 데이터를 유지하며 새 과목만 추가합니다.")
    public ResponseEntity<String> uploadAndParseExcel(
            HttpServletRequest servletRequest,
            @Parameter(description = "종합강의시간표 Excel 파일 (.xlsx)", required = true) @RequestParam("file") MultipartFile file) {
        adminAccessGuard.requireAuthenticated(servletRequest);
        try {
            int savedCount = excelParseService.parseAndSaveSubjectsIncremental(file);
            return ResponseEntity.ok("Excel 파싱 완료. " + savedCount + "개의 과목이 저장되었습니다.");
        } catch (Exception e) {
            log.error("Excel 파싱 실패", e);
            return ResponseEntity.badRequest().body("Excel 파싱에 실패했습니다. 파일 형식과 내용을 확인해주세요.");
        }
    }
}
