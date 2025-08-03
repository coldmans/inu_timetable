package inu.timetable.dto;

import inu.timetable.entity.Schedule;
import inu.timetable.entity.WishlistItem;
import inu.timetable.enums.ClassMethod;
import inu.timetable.enums.SubjectType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

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
    private String department;
    private Integer grade;
    private SubjectType subjectType;
    private ClassMethod classMethod;
    private Boolean isNight;
    private List<ScheduleDto> schedules;
    private String semester;
    private Integer priority;
    private Boolean isRequired;
    private LocalDateTime addedAt;
    
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScheduleDto {
        private Long id;
        private String dayOfWeek;
        private Double startTime;
        private Double endTime;
        
        public static ScheduleDto fromEntity(Schedule schedule) {
            return ScheduleDto.builder()
                .id(schedule.getId())
                .dayOfWeek(schedule.getDayOfWeek())
                .startTime(schedule.getStartTime())
                .endTime(schedule.getEndTime())
                .build();
        }
    }
    
    public static WishlistItemDto fromEntity(WishlistItem wishlistItem) {
        return WishlistItemDto.builder()
            .id(wishlistItem.getId())
            .subjectId(wishlistItem.getSubject().getId())
            .subjectName(wishlistItem.getSubject().getSubjectName())
            .professor(wishlistItem.getSubject().getProfessor())
            .credits(wishlistItem.getSubject().getCredits())
            .department(wishlistItem.getSubject().getDepartment())
            .grade(wishlistItem.getSubject().getGrade())
            .subjectType(wishlistItem.getSubject().getSubjectType())
            .classMethod(wishlistItem.getSubject().getClassMethod())
            .isNight(wishlistItem.getSubject().getIsNight())
            .schedules(wishlistItem.getSubject().getSchedules().stream()
                .map(ScheduleDto::fromEntity)
                .collect(Collectors.toList()))
            .semester(wishlistItem.getSemester())
            .priority(wishlistItem.getPriority())
            .isRequired(wishlistItem.getIsRequired())
            .addedAt(wishlistItem.getAddedAt())
            .build();
    }
}