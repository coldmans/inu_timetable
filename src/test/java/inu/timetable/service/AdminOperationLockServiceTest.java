package inu.timetable.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminOperationLockServiceTest {

    private final AdminOperationLockService adminOperationLockService = new AdminOperationLockService();

    @Test
    void rejectsSameOperationWhileAlreadyRunning() {
        assertThatThrownBy(() -> adminOperationLockService.runExclusive("subject-import-apply",
                () -> adminOperationLockService.runExclusive("subject-import-apply", () -> "inner")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void releasesLockAfterOperationCompletes() throws Exception {
        String first = adminOperationLockService.runExclusive("subject-import-apply", () -> "first");
        String second = adminOperationLockService.runExclusive("subject-import-apply", () -> "second");

        assertThat(first).isEqualTo("first");
        assertThat(second).isEqualTo("second");
    }
}
