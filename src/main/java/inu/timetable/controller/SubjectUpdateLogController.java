package inu.timetable.controller;

import inu.timetable.dto.SubjectUpdateLogResponse;
import inu.timetable.service.SubjectUpdateLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 과목 데이터 업데이트 일지 공개 조회 API. 인증 없이 접근 가능하다.
 */
@RestController
@RequestMapping("/api/subjects/update-logs")
@RequiredArgsConstructor
public class SubjectUpdateLogController {

    private final SubjectUpdateLogService subjectUpdateLogService;

    @GetMapping
    public List<SubjectUpdateLogResponse> getUpdateLogs(@RequestParam(defaultValue = "20") int limit) {
        return subjectUpdateLogService.getRecentLogs(limit);
    }
}
