package inu.timetable.service;

import inu.timetable.entity.AppSetting;
import inu.timetable.repository.AppSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AppSettingService {

    static final String CURRENT_SEMESTER_KEY = "current_semester";
    static final String DEFAULT_SEMESTER = "2026-1";

    private static final Pattern SEMESTER_PATTERN = Pattern.compile("^\\d{4}-[12]$");

    private final AppSettingRepository appSettingRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public String getCurrentSemester() {
        return appSettingRepository.findById(CURRENT_SEMESTER_KEY)
                .map(AppSetting::getSettingValue)
                .orElse(DEFAULT_SEMESTER);
    }

    @Transactional
    public String updateCurrentSemester(String semester) {
        if (semester == null || !SEMESTER_PATTERN.matcher(semester).matches()) {
            throw new IllegalArgumentException("학기 형식이 올바르지 않습니다. (예: 2026-1)");
        }

        AppSetting setting = AppSetting.builder()
                .settingKey(CURRENT_SEMESTER_KEY)
                .settingValue(semester)
                .updatedAt(LocalDateTime.now(clock))
                .build();
        return appSettingRepository.save(setting).getSettingValue();
    }
}
