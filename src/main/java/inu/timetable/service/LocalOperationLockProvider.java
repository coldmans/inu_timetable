package inu.timetable.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "admin.operation-lock.provider", havingValue = "local")
public class LocalOperationLockProvider implements DistributedOperationLockProvider {

    private final Set<String> heldLocks = ConcurrentHashMap.newKeySet();

    @Override
    public Optional<LockHandle> tryAcquire(String operationKey) {
        if (!heldLocks.add(operationKey)) {
            return Optional.empty();
        }
        return Optional.of(() -> heldLocks.remove(operationKey));
    }
}
