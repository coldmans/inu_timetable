package inu.timetable.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class SessionMigrationTokenService {

    private static final int TOKEN_BYTES = 32;
    private static final int MAX_ISSUE_ATTEMPTS = 3;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final SecureRandom secureRandom;

    @Autowired
    public SessionMigrationTokenService(
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate) {
        this(jdbcTemplate, transactionTemplate, new SecureRandom());
    }

    SessionMigrationTokenService(
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate,
            SecureRandom secureRandom) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.secureRandom = secureRandom;
    }

    public String issue(SessionMigrationPrincipal principal, Duration ttl) {
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plus(ttl);
        for (int attempt = 0; attempt < MAX_ISSUE_ATTEMPTS; attempt++) {
            String rawToken = generateToken();
            try {
                jdbcTemplate.update("""
                                INSERT INTO session_migration_tokens (
                                    token_hash,
                                    principal_type,
                                    user_id,
                                    admin_username,
                                    admin_credential_version,
                                    expires_at
                                ) VALUES (?, ?, ?, ?, ?, ?)
                                """,
                        hash(rawToken),
                        principal.type().name(),
                        principal.userId(),
                        principal.adminUsername(),
                        principal.adminCredentialVersion(),
                        expiresAt);
                return rawToken;
            } catch (org.springframework.dao.DuplicateKeyException collision) {
                log.warn("Session migration token collision; retrying");
            }
        }
        throw new IllegalStateException("Failed to issue a unique session migration token");
    }

    /**
     * 토큰 조회와 삭제를 한 트랜잭션의 행 잠금 안에서 처리한다.
     * 동시에 여러 인스턴스로 요청이 들어와도 한 요청만 principal 을 받는다.
     */
    public Optional<SessionMigrationPrincipal> consume(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }

        Optional<SessionMigrationPrincipal> result = transactionTemplate.execute(status -> {
            List<SessionMigrationPrincipal> principals = jdbcTemplate.query("""
                            SELECT principal_type,
                                   user_id,
                                   admin_username,
                                   admin_credential_version
                            FROM session_migration_tokens
                            WHERE token_hash = ?
                              AND expires_at > CURRENT_TIMESTAMP
                            FOR UPDATE
                            """,
                    (resultSet, rowNumber) -> new SessionMigrationPrincipal(
                            SessionMigrationPrincipal.PrincipalType.valueOf(
                                    resultSet.getString("principal_type")),
                            resultSet.getObject("user_id", Long.class),
                            resultSet.getString("admin_username"),
                            resultSet.getObject("admin_credential_version", Long.class)),
                    hash(rawToken));

            if (principals.isEmpty()) {
                jdbcTemplate.update("""
                        DELETE FROM session_migration_tokens
                        WHERE token_hash = ?
                        """, hash(rawToken));
                return Optional.empty();
            }

            jdbcTemplate.update("""
                    DELETE FROM session_migration_tokens
                    WHERE token_hash = ?
                    """, hash(rawToken));
            return Optional.of(principals.get(0));
        });
        return result == null ? Optional.empty() : result;
    }

    public int deleteExpired() {
        return jdbcTemplate.update("""
                DELETE FROM session_migration_tokens
                WHERE expires_at <= CURRENT_TIMESTAMP
                """);
    }

    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        jdbcTemplate.update("""
                DELETE FROM session_migration_tokens
                WHERE token_hash = ?
                """, hash(rawToken));
    }

    @Scheduled(cron = "${session.bridge.cleanup-cron:0 11 * * * *}")
    public void cleanupExpired() {
        int deleted = deleteExpired();
        if (deleted > 0) {
            log.info("Deleted {} expired session migration tokens", deleted);
        }
    }

    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }

    private String generateToken() {
        byte[] token = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }
}
