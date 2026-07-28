package inu.timetable.controller;

import inu.timetable.dto.AdminInquiryPageResponse;
import inu.timetable.dto.InquiryCreateRequest;
import inu.timetable.security.AuthenticatedUser;
import inu.timetable.service.AdminAccessGuard;
import inu.timetable.service.UserInquiryService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 사이트 내 문의 API.
 * 기존 인스타그램 DM 링크 대신 앱 안에서 문의를 남기고, 관리자가 목록 확인/처리한다.
 */
@RestController
@RequiredArgsConstructor
public class InquiryController {

    private final UserInquiryService userInquiryService;
    private final AdminAccessGuard adminAccessGuard;

    /**
     * 문의 접수(비로그인 허용). 로그인 상태면 작성자를 함께 기록한다.
     */
    @PostMapping("/api/inquiries")
    public ResponseEntity<Map<String, String>> submit(
            @RequestBody InquiryCreateRequest request,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            HttpServletRequest servletRequest) {
        Long userId = authenticatedUser != null ? authenticatedUser.id() : null;
        userInquiryService.submit(userId, request.content(), request.contact(), servletRequest);
        return ResponseEntity.ok(Map.of("message", "문의가 접수되었습니다."));
    }

    @GetMapping("/admin/api/inquiries")
    public AdminInquiryPageResponse getInquiries(
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean unresolvedOnly) {
        adminAccessGuard.requireAuthenticated(request);
        return userInquiryService.getInquiries(
                Math.max(0, page), Math.max(1, Math.min(size, 100)), unresolvedOnly);
    }

    @PostMapping("/admin/api/inquiries/{id}/resolve")
    public ResponseEntity<Map<String, String>> resolve(
            HttpServletRequest request,
            @PathVariable Long id) {
        adminAccessGuard.requireAuthenticated(request);
        userInquiryService.resolve(id);
        return ResponseEntity.ok(Map.of("message", "처리 완료로 표시했습니다."));
    }
}
