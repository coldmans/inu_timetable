package inu.timetable.dto;

import inu.timetable.entity.Schedule;
import inu.timetable.entity.Subject;
import inu.timetable.enums.ClassMethod;
import inu.timetable.enums.SubjectType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class SubjectManagementResponse {

    private Long id;
    private String courseCode;
    private String semester;
    private Boolean active;
    private String subjectName;
    private Integer credits;
    private String professor;
    private String department;
    private Integer grade;
    private SubjectType subjectType;
    private ClassMethod classMethod;
    private Boolean isNight;
    private List<ScheduleResponse> schedules;

    public static SubjectManagementResponse from(Subject subject) {
        return SubjectManagementResponse.builder()
                .id(subject.getId())
                .courseCode(subject.getCourseCode())
                .semester(subject.getSemester())
                .active(subject.getActive())
                .subjectName(subject.getSubjectName())
                .credits(subject.getCredits())
                .professor(subject.getProfessor())
                .department(subject.getDepartment())
                .grade(subject.getGrade())
                .subjectType(subject.getSubjectType())
                .classMethod(subject.getClassMethod())
                .isNight(subject.getIsNight())
                .schedules(subject.getSchedules().stream()
                        .map(ScheduleResponse::from)
                        .toList())
                .build();
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class ScheduleResponse {
        private Long id;
        private String dayOfWeek;
        private Double startTime;
        private Double endTime;

        public static ScheduleResponse from(Schedule schedule) {
            return ScheduleResponse.builder()
                    .id(schedule.getId())
                    .dayOfWeek(schedule.getDayOfWeek())
                    .startTime(schedule.getStartTime())
                    .endTime(schedule.getEndTime())
                    .build();
        }
    }
}
