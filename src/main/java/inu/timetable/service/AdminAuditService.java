package inu.timetable.service;

import inu.timetable.entity.AdminAuditLog;
import inu.timetable.repository.AdminAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AdminAuditService {

    private static final int MAX_USERNAME_LENGTH = 100;
    private static final int MAX_METHOD_LENGTH = 12;
    private static final int MAX_PATH_LENGTH = 255;
    private static final int MAX_CLIENT_IP_LENGTH = 64;
    private static final int MAX_USER_AGENT_LENGTH = 255;
    private static final int MAX_ERROR_TYPE_LENGTH = 120;

    private final AdminAuditLogRepository adminAuditLogRepository;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            String adminUsername,
            String method,
            String path,
            int status,
            String clientIp,
            String userAgent,
            String errorType) {
        adminAuditLogRepository.save(AdminAuditLog.builder()
                .adminUsername(limit(adminUsername, MAX_USERNAME_LENGTH))
                .method(limit(method, MAX_METHOD_LENGTH))
                .path(limit(path, MAX_PATH_LENGTH))
                .status(status)
                .success(status < 400)
                .clientIp(limit(clientIp, MAX_CLIENT_IP_LENGTH))
                .userAgent(limit(userAgent, MAX_USER_AGENT_LENGTH))
                .errorType(limit(errorType, MAX_ERROR_TYPE_LENGTH))
                .createdAt(LocalDateTime.now(clock))
                .build());
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
