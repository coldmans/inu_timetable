package inu.timetable.dto;

import inu.timetable.entity.Schedule;
import inu.timetable.entity.Subject;
import inu.timetable.enums.ClassMethod;
import inu.timetable.enums.SubjectType;
import inu.timetable.util.TimeConverter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubjectDto {
    private Long id;
    private String subjectName;
    private Integer credits;
    private String professor;
    private String department;
    private Integer grade;
    private SubjectType subjectType;
    private ClassMethod classMethod;
    private Boolean isNight;
    private List<ScheduleDto> schedules;
    
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScheduleDto {
        private Long id;
        private String dayOfWeek;
        private String startTime;
        private String endTime;
        
        public static ScheduleDto fromEntity(Schedule schedule) {
            return ScheduleDto.builder()
                .id(schedule.getId())
                .dayOfWeek(schedule.getDayOfWeek())
                .startTime(TimeConverter.convertToClockTime(schedule.getStartTime()))
                .endTime(TimeConverter.convertToClockTime(schedule.getEndTime()))
                .build();
        }
    }

    public static SubjectDto from(Subject subject) {
        return SubjectDto.builder()
            .id(subject.getId())
            .subjectName(subject.getSubjectName())
            .credits(subject.getCredits())
            .professor(subject.getProfessor())
            .department(subject.getDepartment())
            .grade(subject.getGrade())
            .subjectType(subject.getSubjectType())
            .classMethod(subject.getClassMethod())
            .isNight(subject.getIsNight())
            .schedules(subject.getSchedules().stream()
                .map(ScheduleDto::fromEntity)
                .collect(Collectors.toList()))
            .build();
    }
}