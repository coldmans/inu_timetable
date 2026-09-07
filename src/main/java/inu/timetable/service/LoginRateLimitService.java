package inu.timetable.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
@Service
public class LoginRateLimitService {

    private static final String NAMESPACE = "USER";

    private final LoginAttemptStore loginAttemptStore;

    public LoginRateLimitService(LoginAttemptStore loginAttemptStore) {
        this.loginAttemptStore = loginAttemptStore;
    }

    @Value("${user.login.max-failures:5}")
    private int maxFailures;

    @Value("${user.login.lock-minutes:10}")
    private long lockMinutes;

    public void rejectIfLocked(String username, HttpServletRequest request) {
        String attemptKey = attemptKey(username, request);
        if (loginAttemptStore.isBlocked(NAMESPACE, attemptKey)) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "로그인 실패가 반복되었습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    public void recordFailure(String username, HttpServletRequest request) {
        loginAttemptStore.recordFailure(
                NAMESPACE,
                attemptKey(username, request),
                maxFailures,
                Duration.ofMinutes(lockMinutes));
    }

    public void recordSuccess(String username, HttpServletRequest request) {
        loginAttemptStore.clear(NAMESPACE, attemptKey(username, request));
    }

    private String attemptKey(String username, HttpServletRequest request) {
        String normalizedUsername = StringUtils.hasText(username) ? username.trim() : "-";
        return normalizedUsername + "@" + clientIp(request);
    }

    private String clientIp(HttpServletRequest request) {
        // X-Forwarded-For 헤더를 직접 신뢰하면 공격자가 매 요청마다 값을 위조해
        // attemptKey 를 무한히 다르게 만들어 계정 잠금/레이트리밋을 우회할 수 있다.
        // 신뢰된 프록시(nginx) 뒤의 실제 IP 보정은 server.forward-headers-strategy=framework
        // (운영 프로파일)가 처리하므로, 여기서는 컨테이너가 인지한 원격 주소만 사용한다.
        return request.getRemoteAddr();
    }
}
