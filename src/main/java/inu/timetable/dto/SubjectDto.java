package inu.timetable.dto;

import inu.timetable.entity.Schedule;
import inu.timetable.entity.Subject;
import inu.timetable.enums.ClassMethod;
import inu.timetable.enums.SubjectType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubjectDto {
    private Long id;
    private String subjectName;
    private Integer credits;
    private String professor;
    private List<Schedule> schedules;
    private Boolean isNight;
    private SubjectType subjectType;
    private ClassMethod classMethod;
    private Integer grade;
    private String department;

    public static SubjectDto from(Subject subject) {
        return SubjectDto.builder()
            .id(subject.getId())
            .subjectName(subject.getSubjectName())
            .credits(subject.getCredits())
            .professor(subject.getProfessor())
            .schedules(subject.getSchedules()) // 스케줄은 별도 API로 처리
            .isNight(subject.getIsNight())
            .subjectType(subject.getSubjectType())
            .classMethod(subject.getClassMethod())
            .grade(subject.getGrade())
            .department(subject.getDepartment())
            .build();
    }
}