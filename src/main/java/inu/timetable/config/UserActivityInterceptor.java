package inu.timetable.config;

import inu.timetable.security.AuthenticatedUser;
import inu.timetable.service.UserActivityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class UserActivityInterceptor implements HandlerInterceptor {

    private final UserActivityService userActivityService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!shouldTrack(request)) {
            return true;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            userActivityService.recordActivity(user.id());
        }

        return true;
    }

    private boolean shouldTrack(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/")
                && !path.startsWith("/api/dev/")
                && !path.startsWith("/api/admin/")
                && !path.equals("/api/auth/csrf");
    }
}
