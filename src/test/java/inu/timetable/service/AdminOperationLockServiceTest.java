package inu.timetable.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminOperationLockServiceTest {

    private final AdminOperationLockService adminOperationLockService =
            new AdminOperationLockService(new InMemoryLockProvider());

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

    private static class InMemoryLockProvider implements DistributedOperationLockProvider {
        private final Set<String> heldLocks = ConcurrentHashMap.newKeySet();

        @Override
        public java.util.Optional<LockHandle> tryAcquire(String operationKey) {
            if (!heldLocks.add(operationKey)) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(() -> heldLocks.remove(operationKey));
        }
    }
}
