package inu.timetable.controller;

import inu.timetable.dto.AdminInquiryFaqResponse;
import inu.timetable.dto.InquiryFaqResponse;
import inu.timetable.dto.InquiryFaqUpsertRequest;
import inu.timetable.service.AdminAccessGuard;
import inu.timetable.service.InquiryFaqService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class InquiryFaqController {

    private final InquiryFaqService inquiryFaqService;
    private final AdminAccessGuard adminAccessGuard;

    @GetMapping("/api/inquiries/faqs")
    public List<InquiryFaqResponse> getPublicFaqs() {
        return inquiryFaqService.getPublicFaqs();
    }

    @GetMapping("/admin/api/inquiry-faqs")
    public List<AdminInquiryFaqResponse> getAdminFaqs(HttpServletRequest request) {
        adminAccessGuard.requireAuthenticated(request);
        return inquiryFaqService.getAdminFaqs();
    }

    @PostMapping("/admin/api/inquiry-faqs")
    public AdminInquiryFaqResponse create(
            HttpServletRequest servletRequest,
            @RequestBody InquiryFaqUpsertRequest request) {
        adminAccessGuard.requireAuthenticated(servletRequest);
        return inquiryFaqService.create(request);
    }

    @PutMapping("/admin/api/inquiry-faqs/{id}")
    public AdminInquiryFaqResponse update(
            HttpServletRequest servletRequest,
            @PathVariable Long id,
            @RequestBody InquiryFaqUpsertRequest request) {
        adminAccessGuard.requireAuthenticated(servletRequest);
        return inquiryFaqService.update(id, request);
    }

    @DeleteMapping("/admin/api/inquiry-faqs/{id}")
    public ResponseEntity<Void> delete(
            HttpServletRequest servletRequest,
            @PathVariable Long id) {
        adminAccessGuard.requireAuthenticated(servletRequest);
        inquiryFaqService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
