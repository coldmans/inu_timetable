package inu.timetable.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import javax.sql.DataSource;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

@Component
@ConditionalOnProperty(
        name = "admin.operation-lock.provider",
        havingValue = "postgres",
        matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class PostgresAdvisoryLockProvider implements DistributedOperationLockProvider {

    private final DataSource dataSource;

    @Override
    public Optional<LockHandle> tryAcquire(String operationKey) {
        long lockKey = stableLockKey(operationKey);
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            if (!tryLock(connection, lockKey)) {
                connection.close();
                return Optional.empty();
            }
            Connection lockConnection = connection;
            return Optional.of(() -> unlockAndClose(lockConnection, lockKey));
        } catch (SQLException exception) {
            closeQuietly(connection);
            throw new IllegalStateException("Failed to acquire PostgreSQL advisory lock", exception);
        }
    }

    static long stableLockKey(String operationKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(operationKey.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(digest).getLong();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }

    private boolean tryLock(Connection connection, long lockKey) throws SQLException {
        try (PreparedStatement statement =
                     connection.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
            statement.setLong(1, lockKey);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getBoolean(1);
            }
        }
    }

    private void unlockAndClose(Connection connection, long lockKey) {
        try (connection;
             PreparedStatement statement =
                     connection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
            statement.setLong(1, lockKey);
            statement.execute();
        } catch (SQLException exception) {
            log.error("Failed to release PostgreSQL advisory lock: key={}", lockKey, exception);
        }
    }

    private void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException exception) {
            log.warn("Failed to close advisory-lock connection", exception);
        }
    }
}
