package inu.timetable.controller;

import inu.timetable.dto.WishlistItemDto;
import inu.timetable.entity.WishlistItem;
import inu.timetable.service.WishlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/wishlist")
@RequiredArgsConstructor
public class WishlistController {
    
    private final WishlistService wishlistService;
    
    @PostMapping("/add")
    public ResponseEntity<?> addToWishlist(@RequestBody Map<String, Object> request) {
        try {
            Long userId = Long.valueOf(request.get("userId").toString());
            Long subjectId = Long.valueOf(request.get("subjectId").toString());
            String semester = (String) request.get("semester");
            Integer priority = request.get("priority") != null ? 
                Integer.valueOf(request.get("priority").toString()) : null;
            Boolean isRequired = request.get("isRequired") != null ? 
                Boolean.valueOf(request.get("isRequired").toString()) : false;
            
            wishlistService.addToWishlist(userId, subjectId, semester, priority, isRequired);
            return ResponseEntity.ok(Map.of("message", "위시리스트에 추가되었습니다."));
            
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    @DeleteMapping("/remove")
    public ResponseEntity<?> removeFromWishlist(@RequestParam Long userId, @RequestParam Long subjectId, @RequestParam String semester) {
        try {
            wishlistService.removeFromWishlist(userId, subjectId, semester);
            return ResponseEntity.ok(Map.of("message", "위시리스트에서 제거되었습니다."));
            
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserWishlist(
            @PathVariable Long userId,
            @RequestParam String semester) {
        try {
            System.out.println(">>> [API] Getting wishlist for userId=" + userId + ", semester=" + semester);
            List<WishlistItem> wishlist = wishlistService.getUserWishlist(userId, semester);
            System.out.println(">>> [API] Found " + wishlist.size() + " wishlist items");
            
            List<WishlistItemDto> wishlistDto = wishlist.stream()
                .map(WishlistItemDto::fromEntity)
                .collect(Collectors.toList());
            
            System.out.println(">>> [API] Returning " + wishlistDto.size() + " wishlist DTOs");
            return ResponseEntity.ok(wishlistDto);
        } catch (Exception e) {
            System.err.println(">>> [API] Error getting wishlist: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    @PutMapping("/priority")
    public ResponseEntity<?> updatePriority(@RequestBody Map<String, Object> request) {
        try {
            Long userId = Long.valueOf(request.get("userId").toString());
            Long subjectId = Long.valueOf(request.get("subjectId").toString());
            String semester = (String) request.get("semester");
            Integer priority = Integer.valueOf(request.get("priority").toString());
            
            wishlistService.updatePriority(userId, subjectId, semester, priority);
            return ResponseEntity.ok(Map.of("message", "우선순위가 업데이트되었습니다."));
            
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    @PutMapping("/required")
    public ResponseEntity<?> updateRequired(@RequestBody Map<String, Object> request) {
        try {
            Long userId = Long.valueOf(request.get("userId").toString());
            Long subjectId = Long.valueOf(request.get("subjectId").toString());
            String semester = (String) request.get("semester");
            Boolean isRequired = Boolean.valueOf(request.get("isRequired").toString());
            
            wishlistService.updateRequired(userId, subjectId, semester, isRequired);
            return ResponseEntity.ok(Map.of("message", "필수 과목 설정이 업데이트되었습니다."));
            
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    @GetMapping("/debug/{userId}")
    public ResponseEntity<?> debugWishlist(@PathVariable Long userId) {
        try {
            List<WishlistItem> allItems = wishlistService.getAllWishlistItems(userId);
            return ResponseEntity.ok(allItems.stream()
                .map(item -> Map.of(
                    "id", item.getId(),
                    "subjectId", item.getSubject().getId(),
                    "subjectName", item.getSubject().getSubjectName(),
                    "semester", item.getSemester(),
                    "isRequired", item.getIsRequired()
                ))
                .collect(Collectors.toList()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}