package inu.timetable.controller;

import inu.timetable.dto.CurrentSemesterResponse;
import inu.timetable.dto.CurrentSemesterUpdateRequest;
import inu.timetable.service.AdminAccessGuard;
import inu.timetable.service.AppSettingService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/api/settings")
@RequiredArgsConstructor
public class AdminSettingsController {

    private final AdminAccessGuard adminAccessGuard;
    private final AppSettingService appSettingService;

    @PutMapping("/current-semester")
    public CurrentSemesterResponse updateCurrentSemester(
            HttpServletRequest servletRequest,
            @RequestBody CurrentSemesterUpdateRequest request) {
        adminAccessGuard.requireAuthenticated(servletRequest);
        return new CurrentSemesterResponse(appSettingService.updateCurrentSemester(request.semester()));
    }
}
