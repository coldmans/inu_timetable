package inu.timetable.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * HTTP 요청에 대한 클라이언트 IP 로깅 필터
 * - 프록시 환경(Vercel, Nginx 등)에서 실제 클라이언트 IP 추출
 * - Actuator 엔드포인트는 로깅에서 제외하여 노이즈 방지
 */
@Slf4j
@Component
@Order(1)
public class RequestLoggingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (request instanceof HttpServletRequest httpRequest) {
            String path = httpRequest.getRequestURI();

            // 1. Actuator 경로는 로그를 남기지 않음 (로그 노이즈 제거)
            if (path.startsWith("/actuator")) {
                chain.doFilter(request, response);
                return;
            }

            // 2. 진짜 유저 IP 추출 (Vercel 프록시 대응)
            String clientIp = httpRequest.getHeader("X-Forwarded-For");
            if (clientIp == null || clientIp.isEmpty()) {
                clientIp = httpRequest.getRemoteAddr();
            } else {
                clientIp = clientIp.split(",")[0].trim(); // 첫 번째 IP가 실제 유저
            }

            log.info("[Request] Method: {}, Path: {}, IP: {}",
                    httpRequest.getMethod(), path, clientIp);
        }

        chain.doFilter(request, response);
    }
}
