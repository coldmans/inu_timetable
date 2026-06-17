package inu.timetable.config;

import inu.timetable.service.AdminAuditService;
import inu.timetable.service.AdminAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAuditInterceptor implements HandlerInterceptor {

    private final AdminAuditService adminAuditService;

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception exception) {
        if (!request.getRequestURI().startsWith("/admin/api/")) {
            return;
        }

        try {
            adminAuditService.record(
                    adminUsername(request),
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    clientIp(request),
                    request.getHeader("User-Agent"),
                    exception == null ? null : exception.getClass().getSimpleName());
        } catch (Exception auditException) {
            log.warn("Failed to record admin audit log for {} {}", request.getMethod(), request.getRequestURI(), auditException);
        }
    }

    private String adminUsername(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object username = session.getAttribute(AdminAuthService.SESSION_USERNAME);
        return username instanceof String text ? text : null;
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
