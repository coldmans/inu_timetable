package inu.timetable.controller;

import inu.timetable.entity.UserTimetable;
import inu.timetable.service.TimetableService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/timetable")
@RequiredArgsConstructor
public class TimetableController {
    
    private final TimetableService timetableService;
    
    @PostMapping("/add")
    public ResponseEntity<?> addSubjectToTimetable(@RequestBody Map<String, Object> request) {
        try {
            Long userId = Long.valueOf(request.get("userId").toString());
            Long subjectId = Long.valueOf(request.get("subjectId").toString());
            String semester = (String) request.get("semester");
            String memo = (String) request.get("memo");
            
            UserTimetable userTimetable = timetableService.addSubjectToTimetable(userId, subjectId, semester, memo);
            return ResponseEntity.ok(userTimetable);
            
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    @DeleteMapping("/remove")
    public ResponseEntity<?> removeSubjectFromTimetable(@RequestParam Long userId, @RequestParam Long subjectId, @RequestParam String semester) {
        try {
            timetableService.removeSubjectFromTimetable(userId, subjectId, semester);
            return ResponseEntity.ok(Map.of("message", "과목이 시간표에서 제거되었습니다."));
            
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<UserTimetable>> getUserTimetable(
            @PathVariable Long userId,
            @RequestParam String semester) {
        
        List<UserTimetable> timetable = timetableService.getUserTimetable(userId, semester);
        return ResponseEntity.ok(timetable);
    }
    
    @PutMapping("/memo")
    public ResponseEntity<?> updateMemo(@RequestBody Map<String, Object> request) {
        try {
            Long userId = Long.valueOf(request.get("userId").toString());
            Long subjectId = Long.valueOf(request.get("subjectId").toString());
            String semester = (String) request.get("semester");
            String memo = (String) request.get("memo");
            
            UserTimetable userTimetable = timetableService.updateMemo(userId, subjectId, semester, memo);
            return ResponseEntity.ok(userTimetable);
            
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    @DeleteMapping("/clear")
    public ResponseEntity<?> clearTimetable(@RequestParam Long userId, @RequestParam String semester) {
        try {
            timetableService.removeAllSubjectsFromTimetable(userId, semester);
            String message = semester != null && !semester.isEmpty() 
                ? semester + " 학기 시간표가 전체 삭제되었습니다." 
                : "전체 시간표가 삭제되었습니다.";
            return ResponseEntity.ok(Map.of("message", message));
            
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}