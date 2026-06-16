package inu.timetable.controller;

import inu.timetable.entity.Subject;
import inu.timetable.security.AuthenticatedUser;
import inu.timetable.security.UserAccessGuard;
import inu.timetable.service.TimetableCombinationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static inu.timetable.util.ApiRequestValues.optionalStringList;
import static inu.timetable.util.ApiRequestValues.requiredInteger;
import static inu.timetable.util.ApiRequestValues.requiredLong;
import static inu.timetable.util.ApiRequestValues.optionalString;

@RestController
@RequestMapping("/api/timetable-combination")
@RequiredArgsConstructor
@Tag(name = "시간표 조합", description = "시간표 자동 조합 생성 API")
public class TimetableCombinationController {

    private final TimetableCombinationService combinationService;
    private final UserAccessGuard userAccessGuard;

    @PostMapping("/generate")
    @Operation(
        summary = "시간표 조합 생성",
        description = "위시리스트 기반으로 목표 학점에 맞는 시간표 조합을 자동 생성합니다. " +
                     "공강 요일을 설정하면 해당 요일에 수업이 없는 조합만 생성됩니다."
    )
    public ResponseEntity<?> generateTimetableCombinations(
            @Parameter(description = "요청 파라미터: userId, semester, targetCredits, maxCombinations(선택), freeDays(선택)")
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        Long userId = requiredLong(request, "userId");
        userAccessGuard.requireMatchingUser(authenticatedUser, userId);
        String semester = optionalString(request, "semester");
        int targetCredits = requiredInteger(request, "targetCredits");
        int maxCombinations = request.containsKey("maxCombinations")
                ? requiredInteger(request, "maxCombinations")
                : 20;
        List<String> freeDays = new ArrayList<>(optionalStringList(request, "freeDays"));

        List<List<Subject>> combinations = combinationService.generateTimetableCombinations(
            userId, semester, targetCredits, maxCombinations, freeDays);

        Map<String, Object> response = new HashMap<>();
        response.put("combinations", combinations);
        response.put("totalCount", combinations.size());
        response.put("targetCredits", targetCredits);
        response.put("freeDays", freeDays);

        List<Map<String, Object>> combinationStats = combinations.stream()
            .map(combinationService::getTimetableStatistics)
            .toList();
        response.put("statistics", combinationStats);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/stats/{userId}")
    public ResponseEntity<?> getTimetableStats(
            @PathVariable Long userId,
            @RequestParam String semester,
            @RequestParam int targetCredits,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {

        userAccessGuard.requireMatchingUser(authenticatedUser, userId);
        List<List<Subject>> combinations = combinationService.generateTimetableCombinations(
            userId, semester, targetCredits, 1);

        if (combinations.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                "message", "생성 가능한 시간표 조합이 없습니다.",
                "possibleCombinations", 0
            ));
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("possibleCombinations", combinations.size());
        stats.put("targetCredits", targetCredits);

        return ResponseEntity.ok(stats);
    }
}
