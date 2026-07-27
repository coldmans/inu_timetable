package inu.timetable.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class JdbcLoginAttemptStore implements LoginAttemptStore {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public boolean isBlocked(String namespace, String identity) {
        String attemptKey = hash(namespace + ":" + identity);
        List<OffsetDateTime> blockedUntilValues = jdbcTemplate.query("""
                        SELECT blocked_until
                        FROM login_rate_limits
                        WHERE attempt_key = ?
                        """,
                (resultSet, rowNumber) ->
                        resultSet.getObject("blocked_until", OffsetDateTime.class),
                attemptKey);
        if (blockedUntilValues.isEmpty() || blockedUntilValues.get(0) == null) {
            return false;
        }

        OffsetDateTime blockedUntil = blockedUntilValues.get(0);
        if (OffsetDateTime.now(ZoneOffset.UTC).isBefore(blockedUntil)) {
            return true;
        }
        jdbcTemplate.update("DELETE FROM login_rate_limits WHERE attempt_key = ?", attemptKey);
        return false;
    }

    @Override
    public void recordFailure(
            String namespace,
            String identity,
            int maxFailures,
            Duration lockDuration) {
        String attemptKey = hash(namespace + ":" + identity);
        OffsetDateTime blockedUntil = OffsetDateTime.now(ZoneOffset.UTC).plus(lockDuration);
        int updated = incrementExisting(attemptKey, maxFailures, blockedUntil);
        if (updated > 0) {
            return;
        }

        try {
            jdbcTemplate.update("""
                            INSERT INTO login_rate_limits (
                                attempt_key,
                                failed_count,
                                blocked_until,
                                updated_at
                            ) VALUES (?, 1, ?, CURRENT_TIMESTAMP)
                            """,
                    attemptKey,
                    maxFailures <= 1 ? blockedUntil : null);
        } catch (DuplicateKeyException concurrentInsert) {
            incrementExisting(attemptKey, maxFailures, blockedUntil);
        }
    }

    @Override
    public void clear(String namespace, String identity) {
        jdbcTemplate.update("""
                DELETE FROM login_rate_limits
                WHERE attempt_key = ?
                """, hash(namespace + ":" + identity));
    }

    @Override
    @Scheduled(cron = "${login.rate-limit.cleanup-cron:0 17 * * * *}")
    public int deleteStale() {
        return jdbcTemplate.update("""
                DELETE FROM login_rate_limits
                WHERE updated_at < CURRENT_TIMESTAMP - INTERVAL '1 day'
                  AND (blocked_until IS NULL OR blocked_until < CURRENT_TIMESTAMP)
                """);
    }

    private int incrementExisting(
            String attemptKey,
            int maxFailures,
            OffsetDateTime blockedUntil) {
        return jdbcTemplate.update("""
                        UPDATE login_rate_limits
                        SET failed_count = failed_count + 1,
                            blocked_until = CASE
                                WHEN failed_count + 1 >= ? THEN ?
                                ELSE blocked_until
                            END,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE attempt_key = ?
                        """,
                maxFailures,
                blockedUntil,
                attemptKey);
    }

    static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }
}
