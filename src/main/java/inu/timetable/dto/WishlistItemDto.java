package inu.timetable.dto;

import inu.timetable.entity.WishlistItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WishlistItemDto {
    private Long id;
    private Long subjectId;
    private String subjectName;
    private String professor;
    private Integer credits;
    private String semester;
    private Integer priority;
    private Boolean isRequired;
    private LocalDateTime addedAt;
    
    public static WishlistItemDto fromEntity(WishlistItem wishlistItem) {
        return WishlistItemDto.builder()
            .id(wishlistItem.getId())
            .subjectId(wishlistItem.getSubject().getId())
            .subjectName(wishlistItem.getSubject().getSubjectName())
            .professor(wishlistItem.getSubject().getProfessor())
            .credits(wishlistItem.getSubject().getCredits())
            .semester(wishlistItem.getSemester())
            .priority(wishlistItem.getPriority())
            .isRequired(wishlistItem.getIsRequired())
            .addedAt(wishlistItem.getAddedAt())
            .build();
    }
}