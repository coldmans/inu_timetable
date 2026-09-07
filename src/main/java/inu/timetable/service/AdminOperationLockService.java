package inu.timetable.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
@Service
public class AdminOperationLockService {

    private final DistributedOperationLockProvider lockProvider;

    public AdminOperationLockService(DistributedOperationLockProvider lockProvider) {
        this.lockProvider = lockProvider;
    }

    public <T> T runExclusive(String operationKey, CheckedSupplier<T> supplier) throws IOException {
        DistributedOperationLockProvider.LockHandle lockHandle =
                lockProvider.tryAcquire(operationKey).orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.CONFLICT,
                                "Same admin operation is already running"));
        try (lockHandle) {
            return supplier.get();
        }
    }

    @FunctionalInterface
    public interface CheckedSupplier<T> {
        T get() throws IOException;
    }
}
