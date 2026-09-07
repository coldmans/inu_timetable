package inu.timetable.service;

import java.time.Duration;

public interface LoginAttemptStore {

    boolean isBlocked(String namespace, String identity);

    void recordFailure(String namespace, String identity, int maxFailures, Duration lockDuration);

    void clear(String namespace, String identity);

    int deleteStale();
}
