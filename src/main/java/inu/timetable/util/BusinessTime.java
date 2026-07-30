package inu.timetable.util;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

public final class BusinessTime {

    public static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private BusinessTime() {
    }

    public static LocalDateTime nowInKorea(Clock clock) {
        return LocalDateTime.ofInstant(clock.instant(), KOREA_ZONE);
    }
}
