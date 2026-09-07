package inu.timetable.service;

import inu.timetable.dto.AdminAuthResponse;
import inu.timetable.entity.AdminAccount;
import inu.timetable.repository.AdminAccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AdminAuthService {

    public static final String SESSION_AUTHENTICATED = "admin_authenticated";
    public static final String SESSION_USERNAME = "admin_username";
    public static final String SESSION_CREDENTIAL_VERSION = "admin_credential_version";
    public static final String SESSION_PASSWORD_CHANGE_REQUIRED = "admin_password_change_required";

    private static final Pattern USERNAME_PATTERN = Pattern.compile("[A-Za-z0-9._-]{4,50}");
    private static final String RATE_LIMIT_NAMESPACE = "ADMIN";
    private static final long BOOTSTRAP_CREDENTIAL_VERSION = 0L;
    private static final int MIN_PASSWORD_LENGTH = 12;
    private static final int MAX_BCRYPT_PASSWORD_BYTES = 72;

    private final BCryptPasswordEncoder passwordEncoder;
    private final LoginAttemptStore loginAttemptStore;
    private final AdminAccountRepository adminAccountRepository;

    @Value("${admin.username:}")
    private String adminUsername;

    @Value("${admin.password-hash:}")
    private String adminPasswordHash;

    @Value("${admin.password:}")
    private String legacyAdminPassword;

    @Value("${admin.login.max-failures:5}")
    private int maxFailures;

    @Value("${admin.login.lock-minutes:10}")
    private long lockMinutes;

    public AdminAuthResponse login(String username, String password, HttpServletRequest request) {
        String attemptKey = attemptKey(username, request);
        rejectIfLocked(attemptKey);
        ActiveCredentials credentials = activeCredentials();

        if (!matches(credentials, username, password)) {
            recordFailure(attemptKey);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid admin credentials");
        }

        loginAttemptStore.clear(RATE_LIMIT_NAMESPACE, attemptKey);
        HttpSession previousSession = request.getSession(false);
        if (previousSession != null) {
            previousSession.invalidate();
        }

        HttpSession session = request.getSession(true);
        request.changeSessionId();

        applySession(session, credentials);

        return response(credentials);
    }

    public AdminAuthResponse currentAuthentication(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        ActiveCredentials credentials = activeCredentials();
        if (!sessionMatches(session, credentials)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin login required");
        }
        return response(credentials);
    }

    @Transactional
    public AdminAuthResponse changeCredentials(
            String currentPassword,
            String newUsername,
            String newPassword,
            String newPasswordConfirm,
            HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        ActiveCredentials current = activeCredentials();
        if (!sessionMatches(session, current)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin login required");
        }
        if (!matches(current, current.username(), currentPassword)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "현재 비밀번호가 올바르지 않습니다.");
        }

        String normalizedUsername = validateUsername(newUsername);
        validateNewPassword(currentPassword, newPassword, newPasswordConfirm);

        LocalDateTime now = LocalDateTime.now();
        AdminAccount account = adminAccountRepository.findById(AdminAccount.SINGLETON_ID)
                .orElseGet(() -> AdminAccount.builder()
                        .id(AdminAccount.SINGLETON_ID)
                        .credentialVersion(BOOTSTRAP_CREDENTIAL_VERSION)
                        .createdAt(now)
                        .build());
        long nextVersion = account.getCredentialVersion() + 1;
        account.changeCredentials(
                normalizedUsername,
                passwordEncoder.encode(newPassword),
                nextVersion,
                now);
        adminAccountRepository.save(account);

        ActiveCredentials changed = ActiveCredentials.persisted(account);
        applySession(session, changed);
        request.changeSessionId();
        return response(changed);
    }

    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    public boolean isAuthenticated(HttpSession session) {
        return session != null && Boolean.TRUE.equals(session.getAttribute(SESSION_AUTHENTICATED));
    }

    public boolean isCurrentAdminSession(HttpSession session) {
        return sessionMatches(session, activeCredentials());
    }

    public boolean isPasswordChangeRequired(HttpSession session) {
        ActiveCredentials credentials = activeCredentials();
        return sessionMatches(session, credentials)
                && passwordChangeRequired(session, credentials);
    }

    public boolean hasAdminAccess(HttpSession session) {
        ActiveCredentials credentials = activeCredentials();
        return sessionMatches(session, credentials)
                && !passwordChangeRequired(session, credentials);
    }

    public boolean restoreMigratedSession(
            String username,
            Long credentialVersion,
            HttpServletRequest request) {
        ActiveCredentials credentials = activeCredentials();
        if (!credentials.username().equals(username)
                || !Objects.equals(credentials.credentialVersion(), credentialVersion)) {
            return false;
        }

        HttpSession session = request.getSession(true);
        applySession(session, credentials);
        return true;
    }

    private ActiveCredentials activeCredentials() {
        return adminAccountRepository.findById(AdminAccount.SINGLETON_ID)
                .map(ActiveCredentials::persisted)
                .orElseGet(() -> ActiveCredentials.bootstrap(
                        adminUsername,
                        adminPasswordHash,
                        legacyAdminPassword));
    }

    private boolean sessionMatches(HttpSession session, ActiveCredentials credentials) {
        if (!isAuthenticated(session)) {
            return false;
        }
        return credentials.username().equals(session.getAttribute(SESSION_USERNAME))
                && Objects.equals(
                        credentials.credentialVersion(),
                        session.getAttribute(SESSION_CREDENTIAL_VERSION));
    }

    private boolean passwordChangeRequired(
            HttpSession session,
            ActiveCredentials credentials) {
        return credentials.passwordChangeRequired()
                || Boolean.TRUE.equals(
                        session.getAttribute(SESSION_PASSWORD_CHANGE_REQUIRED));
    }

    private void applySession(HttpSession session, ActiveCredentials credentials) {
        session.setAttribute(SESSION_AUTHENTICATED, true);
        session.setAttribute(SESSION_USERNAME, credentials.username());
        session.setAttribute(SESSION_CREDENTIAL_VERSION, credentials.credentialVersion());
        session.setAttribute(
                SESSION_PASSWORD_CHANGE_REQUIRED,
                credentials.passwordChangeRequired());
    }

    private boolean matches(
            ActiveCredentials credentials,
            String submittedUsername,
            String submittedPassword) {
        if (!StringUtils.hasText(credentials.username())
                || !credentials.username().equals(submittedUsername)
                || !StringUtils.hasText(submittedPassword)) {
            return false;
        }
        if (StringUtils.hasText(credentials.passwordHash())) {
            return passwordEncoder.matches(submittedPassword, credentials.passwordHash());
        }
        return StringUtils.hasText(credentials.plaintextPassword())
                && constantTimeEquals(submittedPassword, credentials.plaintextPassword());
    }

    private AdminAuthResponse response(ActiveCredentials credentials) {
        return new AdminAuthResponse(
                true,
                credentials.username(),
                credentials.passwordChangeRequired());
    }

    private String validateUsername(String username) {
        String normalized = username == null ? "" : username.trim();
        if (!USERNAME_PATTERN.matcher(normalized).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "관리자 아이디는 영문, 숫자, 마침표, 밑줄, 하이픈으로 4~50자여야 합니다.");
        }
        if ("admin".equalsIgnoreCase(normalized)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "admin은 관리자 아이디로 사용할 수 없습니다.");
        }
        return normalized;
    }

    private void validateNewPassword(
            String currentPassword,
            String newPassword,
            String newPasswordConfirm) {
        if (!constantTimeEquals(newPassword, newPasswordConfirm)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "새 비밀번호 확인이 일치하지 않습니다.");
        }
        if (!StringUtils.hasText(newPassword)
                || newPassword.length() < MIN_PASSWORD_LENGTH
                || newPassword.getBytes(StandardCharsets.UTF_8).length > MAX_BCRYPT_PASSWORD_BYTES
                || newPassword.chars().noneMatch(Character::isLetter)
                || newPassword.chars().noneMatch(Character::isDigit)
                || newPassword.chars().allMatch(Character::isLetterOrDigit)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "새 비밀번호는 12자 이상이며 문자, 숫자, 특수문자를 포함해야 합니다.");
        }
        if (constantTimeEquals(newPassword, currentPassword)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "현재 비밀번호와 다른 새 비밀번호를 사용해주세요.");
        }
    }

    private boolean constantTimeEquals(String actual, String expected) {
        byte[] actualBytes = String.valueOf(actual).getBytes(StandardCharsets.UTF_8);
        byte[] expectedBytes = String.valueOf(expected).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(actualBytes, expectedBytes);
    }

    private void rejectIfLocked(String attemptKey) {
        if (loginAttemptStore.isBlocked(RATE_LIMIT_NAMESPACE, attemptKey)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many failed admin login attempts. Try again later.");
        }
    }

    private void recordFailure(String attemptKey) {
        loginAttemptStore.recordFailure(
                RATE_LIMIT_NAMESPACE,
                attemptKey,
                maxFailures,
                Duration.ofMinutes(lockMinutes));
    }

    private String attemptKey(String username, HttpServletRequest request) {
        String normalizedUsername = StringUtils.hasText(username) ? username.trim() : "-";
        return normalizedUsername + "@" + clientIp(request);
    }

    private String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    private record ActiveCredentials(
            String username,
            String passwordHash,
            String plaintextPassword,
            boolean passwordChangeRequired,
            Long credentialVersion) {

        private static ActiveCredentials bootstrap(
                String username,
                String passwordHash,
                String plaintextPassword) {
            return new ActiveCredentials(
                    StringUtils.hasText(username) ? username : "",
                    passwordHash,
                    plaintextPassword,
                    true,
                    BOOTSTRAP_CREDENTIAL_VERSION);
        }

        private static ActiveCredentials persisted(AdminAccount account) {
            return new ActiveCredentials(
                    account.getUsername(),
                    account.getPasswordHash(),
                    null,
                    account.isPasswordChangeRequired(),
                    account.getCredentialVersion());
        }

    }
}
