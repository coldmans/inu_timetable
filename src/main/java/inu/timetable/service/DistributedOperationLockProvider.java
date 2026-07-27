package inu.timetable.service;

import java.util.Optional;

public interface DistributedOperationLockProvider {

    Optional<LockHandle> tryAcquire(String operationKey);

    @FunctionalInterface
    interface LockHandle extends AutoCloseable {
        @Override
        void close();
    }
}
