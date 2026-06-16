package inu.timetable.controller;

import inu.timetable.dto.WishlistItemDto;
import inu.timetable.entity.WishlistItem;
import inu.timetable.security.AuthenticatedUser;
import inu.timetable.security.UserAccessGuard;
import inu.timetable.service.WishlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static inu.timetable.util.ApiRequestValues.optionalBoolean;
import static inu.timetable.util.ApiRequestValues.optionalInteger;
import static inu.timetable.util.ApiRequestValues.optionalString;
import static inu.timetable.util.ApiRequestValues.requiredInteger;
import static inu.timetable.util.ApiRequestValues.requiredLong;

@RestController
@RequestMapping("/api/wishlist")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistService wishlistService;
    private final UserAccessGuard userAccessGuard;

    @PostMapping("/add")
    public ResponseEntity<?> addToWishlist(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        Long userId = requiredLong(request, "userId");
        userAccessGuard.requireMatchingUser(authenticatedUser, userId);
        Long subjectId = requiredLong(request, "subjectId");
        String semester = optionalString(request, "semester");
        Integer priority = optionalInteger(request, "priority");
        Boolean isRequired = optionalBoolean(request, "isRequired", false);

        wishlistService.addToWishlist(userId, subjectId, semester, priority, isRequired);
        return ResponseEntity.ok(Map.of("message", "위시리스트에 추가되었습니다."));
    }

    @DeleteMapping("/remove")
    public ResponseEntity<?> removeFromWishlist(
            @RequestParam Long userId,
            @RequestParam Long subjectId,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        userAccessGuard.requireMatchingUser(authenticatedUser, userId);
        wishlistService.removeFromWishlist(userId, subjectId);
        return ResponseEntity.ok(Map.of("message", "위시리스트에서 제거되었습니다."));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserWishlist(
            @PathVariable Long userId,
            @RequestParam String semester,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        userAccessGuard.requireMatchingUser(authenticatedUser, userId);
        List<WishlistItem> wishlist = wishlistService.getUserWishlist(userId, semester);
        List<WishlistItemDto> wishlistDto = wishlist.stream()
            .map(WishlistItemDto::fromEntity)
            .collect(Collectors.toList());

        return ResponseEntity.ok(wishlistDto);
    }

    @PutMapping("/priority")
    public ResponseEntity<?> updatePriority(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        Long userId = requiredLong(request, "userId");
        userAccessGuard.requireMatchingUser(authenticatedUser, userId);
        Long subjectId = requiredLong(request, "subjectId");
        Integer priority = requiredInteger(request, "priority");

        wishlistService.updatePriority(userId, subjectId, priority);
        return ResponseEntity.ok(Map.of("message", "우선순위가 업데이트되었습니다."));
    }

    @PutMapping("/required")
    public ResponseEntity<?> updateRequired(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        Long userId = requiredLong(request, "userId");
        userAccessGuard.requireMatchingUser(authenticatedUser, userId);
        Long subjectId = requiredLong(request, "subjectId");
        Boolean isRequired = optionalBoolean(request, "isRequired", false);

        wishlistService.updateRequired(userId, subjectId, isRequired);
        return ResponseEntity.ok(Map.of("message", "필수 과목 설정이 업데이트되었습니다."));
    }
}
