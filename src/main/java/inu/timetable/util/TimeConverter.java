package inu.timetable.util;

public class TimeConverter {
    
    public static String convertToClockTime(Double time) {
        if (time == null) return "";
        
        if (time >= 10.0) { // 야간 시간 (10.0 = 18:00)
            int hour = 18 + (int)Math.floor(time - 10.0);
            int minute = (time % 1.0 == 0.5) ? 30 : 0;
            return String.format("%02d:%02d", hour, minute);
        } else { // 일반 시간 (1.0 = 09:00)
            int hour = 8 + (int)Math.floor(time);
            int minute = (time % 1.0 == 0.5) ? 30 : 0;
            return String.format("%02d:%02d", hour, minute);
        }
    }
}