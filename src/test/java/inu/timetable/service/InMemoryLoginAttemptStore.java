package inu.timetable.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class InMemoryLoginAttemptStore implements LoginAttemptStore {

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    @Override
    public boolean isBlocked(String namespace, String identity) {
        Attempt attempt = attempts.get(key(namespace, identity));
        if (attempt == null || attempt.blockedUntil == null) {
            return false;
        }
        if (Instant.now().isBefore(attempt.blockedUntil)) {
            return true;
        }
        attempts.remove(key(namespace, identity));
        return false;
    }

    @Override
    public void recordFailure(
            String namespace,
            String identity,
            int maxFailures,
            Duration lockDuration) {
        attempts.compute(key(namespace, identity), (key, current) -> {
            Attempt attempt = current == null ? new Attempt() : current;
            attempt.failedCount++;
            if (attempt.failedCount >= maxFailures) {
                attempt.blockedUntil = Instant.now().plus(lockDuration);
            }
            return attempt;
        });
    }

    @Override
    public void clear(String namespace, String identity) {
        attempts.remove(key(namespace, identity));
    }

    @Override
    public int deleteStale() {
        return 0;
    }

    private String key(String namespace, String identity) {
        return namespace + ":" + identity;
    }

    private static class Attempt {
        private int failedCount;
        private Instant blockedUntil;
    }
}
