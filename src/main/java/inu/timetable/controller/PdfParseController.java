package inu.timetable.controller;

import inu.timetable.service.PdfParseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/pdf")
@RequiredArgsConstructor
public class PdfParseController {

    private final PdfParseService pdfParseService;

    @PostMapping("/upload")
    public ResponseEntity<String> uploadAndParsePdf(@RequestParam("file") MultipartFile file) {
        try {
            int savedCount = pdfParseService.parseAndSaveSubjects(file);
            return ResponseEntity.ok("PDF 파싱 완료. " + savedCount + "개의 과목이 저장되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("PDF 파싱 실패: " + e.getMessage());
        }
    }
}