package inu.timetable.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@Import(JdbcLoginAttemptStore.class)
class JdbcLoginAttemptStoreTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void separateStoreInstancesShareFailureAndUnlockState() {
        LoginAttemptStore firstInstance = new JdbcLoginAttemptStore(jdbcTemplate);
        LoginAttemptStore secondInstance = new JdbcLoginAttemptStore(jdbcTemplate);

        firstInstance.recordFailure("USER", "student@127.0.0.1", 2, Duration.ofMinutes(10));
        secondInstance.recordFailure("USER", "student@127.0.0.1", 2, Duration.ofMinutes(10));

        assertThat(firstInstance.isBlocked("USER", "student@127.0.0.1")).isTrue();
        assertThat(secondInstance.isBlocked("USER", "student@127.0.0.1")).isTrue();

        secondInstance.clear("USER", "student@127.0.0.1");

        assertThat(firstInstance.isBlocked("USER", "student@127.0.0.1")).isFalse();
        String storedKey = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM login_rate_limits",
                Integer.class).toString();
        assertThat(storedKey).isEqualTo("0");
    }
}
