package inu.timetable.controller;

import inu.timetable.dto.CurrentSemesterResponse;
import inu.timetable.service.AppSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final AppSettingService appSettingService;

    @GetMapping("/current-semester")
    public CurrentSemesterResponse getCurrentSemester() {
        return new CurrentSemesterResponse(appSettingService.getCurrentSemester());
    }
}
