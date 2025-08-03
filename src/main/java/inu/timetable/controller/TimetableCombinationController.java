package inu.timetable.controller;

import inu.timetable.entity.Subject;
import inu.timetable.service.TimetableCombinationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/timetable-combination")
@RequiredArgsConstructor
public class TimetableCombinationController {
    
    private final TimetableCombinationService combinationService;
    
    @PostMapping("/generate")
    public ResponseEntity<?> generateTimetableCombinations(@RequestBody Map<String, Object> request) {
        try {
            Long userId = Long.valueOf(request.get("userId").toString());
            String semester = (String) request.get("semester");
            int targetCredits = Integer.valueOf(request.get("targetCredits").toString());
            int maxCombinations = request.containsKey("maxCombinations") ? 
                Integer.valueOf(request.get("maxCombinations").toString()) : 10;
            
            List<List<Subject>> combinations = combinationService.generateTimetableCombinations(
                userId, semester, targetCredits, maxCombinations);
            
            Map<String, Object> response = new HashMap<>();
            response.put("combinations", combinations);
            response.put("totalCount", combinations.size());
            response.put("targetCredits", targetCredits);
            
            // 각 조합에 대한 통계 추가
            List<Map<String, Object>> combinationStats = combinations.stream()
                .map(combinationService::getTimetableStatistics)
                .toList();
            response.put("statistics", combinationStats);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    @GetMapping("/stats/{userId}")
    public ResponseEntity<?> getTimetableStats(
            @PathVariable Long userId,
            @RequestParam String semester,
            @RequestParam int targetCredits) {
        
        try {
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
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}