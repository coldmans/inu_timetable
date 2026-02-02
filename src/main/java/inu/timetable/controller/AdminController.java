package inu.timetable.controller;

import inu.timetable.service.ExcelParseService;
import inu.timetable.service.ParsingValidationService;
import inu.timetable.service.PdfParseService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

/**
 * 관리자 페이지 컨트롤러 (Thymeleaf)
 * PDF/Excel 업로드 및 파싱 검증 관리
 */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private static final String ADMIN_PASSWORD = "155788";
    private static final String SESSION_AUTHENTICATED = "admin_authenticated";

    private final PdfParseService pdfParseService;
    private final ExcelParseService excelParseService;
    private final ParsingValidationService validationService;

    /**
     * 로그인 페이지
     */
    @GetMapping("/login")
    public String loginPage(HttpSession session) {
        // 이미 인증된 경우 업로드 페이지로 리다이렉트
        if (isAuthenticated(session)) {
            return "redirect:/admin/upload";
        }
        return "admin/login";
    }

    /**
     * 로그인 처리
     */
    @PostMapping("/login")
    public String login(@RequestParam String password, HttpSession session, RedirectAttributes redirectAttributes) {
        if (ADMIN_PASSWORD.equals(password)) {
            session.setAttribute(SESSION_AUTHENTICATED, true);
            return "redirect:/admin/upload";
        } else {
            redirectAttributes.addFlashAttribute("error", "비밀번호가 올바르지 않습니다.");
            return "redirect:/admin/login";
        }
    }

    /**
     * 로그아웃
     */
    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/admin/login";
    }

    /**
     * 업로드 페이지
     */
    @GetMapping("/upload")
    public String uploadPage(HttpSession session, Model model) {
        if (!isAuthenticated(session)) {
            return "redirect:/admin/login";
        }
        return "admin/upload";
    }

    /**
     * PDF 업로드 처리
     * 
     * @param mode "incremental" (기본값) 또는 "replace"
     */
    @PostMapping("/upload/pdf")
    public String uploadPdf(@RequestParam("file") MultipartFile file,
            @RequestParam(value = "mode", defaultValue = "incremental") String mode,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!isAuthenticated(session)) {
            return "redirect:/admin/login";
        }

        try {
            int count;
            String modeDescription;

            if ("replace".equalsIgnoreCase(mode)) {
                count = pdfParseService.parseAndSaveSubjectsReplace(file);
                modeDescription = "전체 교체";
            } else {
                count = pdfParseService.parseAndSaveSubjectsIncremental(file);
                modeDescription = "증분 추가";
            }

            redirectAttributes.addFlashAttribute("success",
                    "PDF 파싱 완료 (" + modeDescription + ")! " + count + "개의 과목이 처리되었습니다.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "PDF 파싱 실패: " + e.getMessage());
        }

        return "redirect:/admin/upload";
    }

    /**
     * Excel 업로드 처리
     * 
     * @param mode "replace" (기본값) 또는 "incremental"
     */
    @PostMapping("/upload/excel")
    public String uploadExcel(@RequestParam("file") MultipartFile file,
            @RequestParam(value = "mode", defaultValue = "replace") String mode,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!isAuthenticated(session)) {
            return "redirect:/admin/login";
        }

        try {
            int count;
            String modeDescription;

            if ("incremental".equalsIgnoreCase(mode)) {
                count = excelParseService.parseAndSaveSubjectsIncremental(file);
                modeDescription = "증분 추가";
            } else {
                count = excelParseService.parseAndSaveSubjectsReplace(file);
                modeDescription = "전체 교체";
            }

            redirectAttributes.addFlashAttribute("success",
                    "Excel 파싱 완료 (" + modeDescription + ")! " + count + "개의 과목이 처리되었습니다.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Excel 파싱 실패: " + e.getMessage());
        }

        return "redirect:/admin/upload";
    }

    /**
     * 파싱 검증 페이지
     */
    @GetMapping("/validate")
    public String validatePage(HttpSession session) {
        if (!isAuthenticated(session)) {
            return "redirect:/admin/login";
        }
        return "admin/validate";
    }

    /**
     * PDF 파싱 검증
     */
    @PostMapping("/validate/pdf")
    public String validatePdf(@RequestParam("file") MultipartFile file,
            @RequestParam(value = "semester", required = false) String semester,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (!isAuthenticated(session)) {
            return "redirect:/admin/login";
        }

        try {
            System.out.println("=== PDF 검증 시작 ===");
            System.out.println("파일명: " + file.getOriginalFilename());
            System.out.println("파일 크기: " + file.getSize() + " bytes");

            Map<String, Object> report = validationService.validatePdfParsing(file, semester);

            System.out.println("검증 완료 - 파싱된 과목 수: " + report.get("parsedCount"));
            System.out.println("DB 과목 수: " + report.get("dbCount"));

            model.addAttribute("report", report);
            model.addAttribute("fileType", "PDF");
            return "admin/validate-result";
        } catch (Exception e) {
            System.err.println("PDF 검증 실패: " + e.getMessage());
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error",
                    "PDF 검증 실패: " + e.getMessage());
            return "redirect:/admin/validate";
        }
    }

    /**
     * Excel 파싱 검증
     */
    @PostMapping("/validate/excel")
    public String validateExcel(@RequestParam("file") MultipartFile file,
            @RequestParam(value = "semester", required = false) String semester,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (!isAuthenticated(session)) {
            return "redirect:/admin/login";
        }

        try {
            Map<String, Object> report = validationService.validateExcelParsing(file, semester, 0);
            model.addAttribute("report", report);
            model.addAttribute("fileType", "Excel");
            return "admin/validate-result";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Excel 검증 실패: " + e.getMessage());
            return "redirect:/admin/validate";
        }
    }

    /**
     * 인증 확인
     */
    private boolean isAuthenticated(HttpSession session) {
        Boolean authenticated = (Boolean) session.getAttribute(SESSION_AUTHENTICATED);
        return authenticated != null && authenticated;
    }

    /**
     * 누락 과목 분석 API (엑셀 vs DB 비교)
     * GET /admin/api/analyze-missing
     */
    @GetMapping("/api/analyze-missing")
    @ResponseBody
    public Map<String, Object> analyzeMissingSubjects() {
        return validationService.analyzeMissingSubjects();
    }

    /**
     * 엑셀 헤더 확인 API (컬럼 구조 디버깅용)
     * GET /admin/api/excel-headers
     */
    @GetMapping("/api/excel-headers")
    @ResponseBody
    public Map<String, Object> getExcelHeaders() {
        return validationService.getExcelHeaders();
    }

    /**
     * 누락 과목 DB 삽입 API
     * POST /admin/api/insert-missing
     */
    @PostMapping("/api/insert-missing")
    @ResponseBody
    public Map<String, Object> insertMissingSubjects() {
        return validationService.insertMissingSubjects();
    }
}
