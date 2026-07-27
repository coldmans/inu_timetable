package inu.timetable.service;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostgresAdvisoryLockProviderTest {

    @Test
    void acquiredLockIsReleasedOnTheSameConnection() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement acquireStatement = mock(PreparedStatement.class);
        PreparedStatement releaseStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement("SELECT pg_try_advisory_lock(?)"))
                .thenReturn(acquireStatement);
        when(connection.prepareStatement("SELECT pg_advisory_unlock(?)"))
                .thenReturn(releaseStatement);
        when(acquireStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getBoolean(1)).thenReturn(true);

        var handle = new PostgresAdvisoryLockProvider(dataSource)
                .tryAcquire("subject-import-apply");

        assertThat(handle).isPresent();
        handle.orElseThrow().close();

        verify(acquireStatement).setLong(
                1,
                PostgresAdvisoryLockProvider.stableLockKey("subject-import-apply"));
        verify(releaseStatement).setLong(
                1,
                PostgresAdvisoryLockProvider.stableLockKey("subject-import-apply"));
        verify(connection).close();
    }

    @Test
    void rejectedLockClosesConnectionWithoutRunningUnlock() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement acquireStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement("SELECT pg_try_advisory_lock(?)"))
                .thenReturn(acquireStatement);
        when(acquireStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getBoolean(1)).thenReturn(false);

        var handle = new PostgresAdvisoryLockProvider(dataSource)
                .tryAcquire("subject-import-apply");

        assertThat(handle).isEmpty();
        verify(connection).close();
        verify(connection, never()).prepareStatement("SELECT pg_advisory_unlock(?)");
    }

    @Test
    void stableLockKeyIsDeterministic() {
        assertThat(PostgresAdvisoryLockProvider.stableLockKey("subject-import-apply"))
                .isEqualTo(PostgresAdvisoryLockProvider.stableLockKey("subject-import-apply"))
                .isNotEqualTo(PostgresAdvisoryLockProvider.stableLockKey("subject-delete"));
    }
}
