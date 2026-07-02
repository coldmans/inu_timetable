package inu.timetable.controller;

import inu.timetable.service.AdminAuthService;
import inu.timetable.service.AdminOperationLockService;
import inu.timetable.service.ExcelParseService;
import inu.timetable.service.OfficialSubjectImportService;
import inu.timetable.service.ParsingValidationService;
import inu.timetable.service.PdfParseService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * 관리자 페이지 컨트롤러 (Thymeleaf)
 * PDF/Excel 업로드 및 파싱 검증 관리
 */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminAuthService adminAuthService;
    private final AdminOperationLockService adminOperationLockService;
    private final PdfParseService pdfParseService;
    private final ExcelParseService excelParseService;
    private final OfficialSubjectImportService officialSubjectImportService;
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
    public String login(
            @RequestParam String username,
            @RequestParam String password,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        try {
            adminAuthService.login(username, password, request);
            return "redirect:/admin/upload";
        } catch (ResponseStatusException e) {
            String message = e.getStatusCode().equals(HttpStatus.TOO_MANY_REQUESTS)
                    ? "로그인 실패가 너무 많습니다. 잠시 후 다시 시도해주세요."
                    : "관리자 계정 정보가 올바르지 않습니다.";
            redirectAttributes.addFlashAttribute("error", message);
            return "redirect:/admin/login";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "비밀번호가 올바르지 않습니다.");
            return "redirect:/admin/login";
        }
    }

    /**
     * 로그아웃
     */
    @PostMapping("/logout")
    public String logout(HttpServletRequest request) {
        adminAuthService.logout(request);
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
     * 분석 대시보드 페이지 (DAU/MAU·이벤트·인기 검색어)
     */
    @GetMapping("/dashboard")
    public String dashboardPage(HttpSession session) {
        if (!isAuthenticated(session)) {
            return "redirect:/admin/login";
        }
        return "admin/dashboard";
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
            @RequestParam String semester,
            @RequestParam(value = "deactivateMissing", defaultValue = "false") boolean deactivateMissing,
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
                count = adminOperationLockService.runExclusive("subject-import-apply",
                        () -> officialSubjectImportService.apply(file, semester, deactivateMissing)).getTotalRows();
                modeDescription = "학수번호 기준 전체 반영";
            }

            redirectAttributes.addFlashAttribute("success",
                    "Excel 처리 완료 (" + modeDescription + ", " + semester + ")! " + count + "개의 과목이 처리되었습니다.");
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
        return adminAuthService.isAuthenticated(session);
    }

    /**
     * 누락 과목 분석 API (엑셀 vs DB 비교)
     * GET /admin/api/analyze-missing
     */
    @GetMapping("/api/analyze-missing")
    @ResponseBody
    public Map<String, Object> analyzeMissingSubjects(HttpSession session) {
        requireAuthenticated(session);
        return validationService.analyzeMissingSubjects();
    }

    /**
     * 엑셀 헤더 확인 API (컬럼 구조 디버깅용)
     * GET /admin/api/excel-headers
     */
    @GetMapping("/api/excel-headers")
    @ResponseBody
    public Map<String, Object> getExcelHeaders(HttpSession session) {
        requireAuthenticated(session);
        return validationService.getExcelHeaders();
    }

    /**
     * 누락 과목 DB 삽입 API
     * POST /admin/api/insert-missing
     */
    @PostMapping("/api/insert-missing")
    @ResponseBody
    public Map<String, Object> insertMissingSubjects(HttpSession session) {
        requireAuthenticated(session);
        return validationService.insertMissingSubjects();
    }

    private void requireAuthenticated(HttpSession session) {
        if (!isAuthenticated(session)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin login required");
        }
    }
}
