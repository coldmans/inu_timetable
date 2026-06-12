package inu.timetable.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AdminOperationLockService {

    private final Set<String> runningOperations = ConcurrentHashMap.newKeySet();

    public <T> T runExclusive(String operationKey, CheckedSupplier<T> supplier) throws IOException {
        if (!runningOperations.add(operationKey)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Same admin operation is already running");
        }
        try {
            return supplier.get();
        } finally {
            runningOperations.remove(operationKey);
        }
    }

    @FunctionalInterface
    public interface CheckedSupplier<T> {
        T get() throws IOException;
    }
}
