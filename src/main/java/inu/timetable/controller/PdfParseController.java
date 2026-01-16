package inu.timetable.controller;

import inu.timetable.service.ExcelParseService;
import inu.timetable.service.PdfParseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "파일 업로드", description = "PDF 및 Excel 파일 업로드 및 파싱 API")
public class PdfParseController {

    private final PdfParseService pdfParseService;
    private final ExcelParseService excelParseService;

    @PostMapping(value = "/pdf/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "PDF 파일 업로드", description = "수강편람 PDF 파일을 업로드하여 과목 데이터를 파싱하고 저장합니다. Gemini AI를 사용하여 파싱합니다.")
    public ResponseEntity<String> uploadAndParsePdf(
            @Parameter(description = "수강편람 PDF 파일", required = true)
            @RequestParam("file") MultipartFile file) {
        try {
            int savedCount = pdfParseService.parseAndSaveSubjects(file);
            return ResponseEntity.ok("PDF 파싱 완료. " + savedCount + "개의 과목이 저장되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("PDF 파싱 실패: " + e.getMessage());
        }
    }

    @PostMapping(value = "/excel/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Excel 파일 업로드", description = "종합강의시간표 Excel 파일(.xlsx)을 업로드하여 과목 데이터를 파싱하고 저장합니다.")
    public ResponseEntity<String> uploadAndParseExcel(
            @Parameter(description = "종합강의시간표 Excel 파일 (.xlsx)", required = true)
            @RequestParam("file") MultipartFile file) {
        try {
            int savedCount = excelParseService.parseAndSaveSubjects(file);
            return ResponseEntity.ok("Excel 파싱 완료. " + savedCount + "개의 과목이 저장되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Excel 파싱 실패: " + e.getMessage());
        }
    }
}