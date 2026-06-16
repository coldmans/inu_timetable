package inu.timetable.controller;

import inu.timetable.entity.UserTimetable;
import inu.timetable.security.AuthenticatedUser;
import inu.timetable.security.UserAccessGuard;
import inu.timetable.service.TimetableService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static inu.timetable.util.ApiRequestValues.optionalString;
import static inu.timetable.util.ApiRequestValues.requiredLong;

@RestController
@RequestMapping("/api/timetable")
@RequiredArgsConstructor
public class TimetableController {

    private final TimetableService timetableService;
    private final UserAccessGuard userAccessGuard;

    @PostMapping("/add")
    public ResponseEntity<?> addSubjectToTimetable(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        Long userId = requiredLong(request, "userId");
        userAccessGuard.requireMatchingUser(authenticatedUser, userId);
        Long subjectId = requiredLong(request, "subjectId");
        String semester = optionalString(request, "semester");
        String memo = optionalString(request, "memo");

        UserTimetable userTimetable = timetableService.addSubjectToTimetable(userId, subjectId, semester, memo);
        return ResponseEntity.ok(userTimetable);
    }

    @DeleteMapping("/remove")
    public ResponseEntity<?> removeSubjectFromTimetable(
            @RequestParam Long userId,
            @RequestParam Long subjectId,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        userAccessGuard.requireMatchingUser(authenticatedUser, userId);
        timetableService.removeSubjectFromTimetable(userId, subjectId);
        return ResponseEntity.ok(Map.of("message", "과목이 시간표에서 제거되었습니다."));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<UserTimetable>> getUserTimetable(
            @PathVariable Long userId,
            @RequestParam(required = false) String semester,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {

        userAccessGuard.requireMatchingUser(authenticatedUser, userId);
        List<UserTimetable> timetable = timetableService.getUserTimetable(userId, semester);
        return ResponseEntity.ok(timetable);
    }

    @PutMapping("/memo")
    public ResponseEntity<?> updateMemo(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        Long userId = requiredLong(request, "userId");
        userAccessGuard.requireMatchingUser(authenticatedUser, userId);
        Long subjectId = requiredLong(request, "subjectId");
        String memo = optionalString(request, "memo");

        UserTimetable userTimetable = timetableService.updateMemo(userId, subjectId, memo);
        return ResponseEntity.ok(userTimetable);
    }

    @DeleteMapping("/clear")
    public ResponseEntity<?> clearTimetable(
            @RequestParam Long userId,
            @RequestParam(required = false) String semester,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        userAccessGuard.requireMatchingUser(authenticatedUser, userId);
        timetableService.removeAllSubjectsFromTimetable(userId, semester);
        String message = semester != null && !semester.isEmpty()
            ? semester + " 학기 시간표가 전체 삭제되었습니다."
            : "전체 시간표가 삭제되었습니다.";
        return ResponseEntity.ok(Map.of("message", message));
    }
}
