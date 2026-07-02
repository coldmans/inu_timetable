package inu.timetable.controller;

import inu.timetable.dto.AnalyticsSummaryResponse;
import inu.timetable.enums.AnalyticsEventType;
import inu.timetable.security.AuthenticatedUser;
import inu.timetable.service.AdminAccessGuard;
import inu.timetable.service.AnalyticsService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final AdminAccessGuard adminAccessGuard;

    /**
     * 클라이언트 이벤트 트래킹(비로그인 허용). 잘못된 타입/과대 페이로드는 조용히 무시하여
     * 분석 수집이 사용자 흐름을 방해하지 않게 한다.
     */
    @PostMapping("/api/events")
    public ResponseEntity<Void> track(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        AnalyticsEventType type = parseType(body.get("eventType"));
        if (type == null) {
            return ResponseEntity.noContent().build();
        }
        String label = body.get("label") instanceof String s ? s : null;
        String sessionId = body.get("sessionId") instanceof String s ? s : null;
        Long userId = authenticatedUser != null ? authenticatedUser.id() : null;

        analyticsService.record(type, userId, label, sessionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/admin/api/analytics/summary")
    public AnalyticsSummaryResponse summary(
            HttpServletRequest request,
            @RequestParam(defaultValue = "14") int days) {
        adminAccessGuard.requireAuthenticated(request);
        return analyticsService.summary(Math.max(1, Math.min(days, 90)));
    }

    private AnalyticsEventType parseType(Object raw) {
        if (!(raw instanceof String text) || text.isBlank()) {
            return null;
        }
        try {
            return AnalyticsEventType.valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
