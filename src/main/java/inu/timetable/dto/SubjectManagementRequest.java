package inu.timetable.dto;

import inu.timetable.enums.ClassMethod;
import inu.timetable.enums.SubjectType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SubjectManagementRequest {

    @NotBlank
    @Size(max = 200)
    private String subjectName;

    @NotNull
    @Min(0)
    @Max(10)
    private Integer credits;

    @NotBlank
    @Size(max = 100)
    private String professor;

    @Size(max = 100)
    private String department;

    @Min(0)
    @Max(10)
    private Integer grade;

    @NotNull
    private SubjectType subjectType;

    @NotNull
    private ClassMethod classMethod;

    @NotNull
    private Boolean isNight;

    @NotNull
    private List<@Valid ScheduleRequest> schedules = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScheduleRequest {

        @NotBlank
        @Pattern(regexp = "[월화수목금토일]")
        private String dayOfWeek;

        @NotNull
        @DecimalMin("0.0")
        @DecimalMax("20.0")
        private Double startTime;

        @NotNull
        @DecimalMin("0.0")
        @DecimalMax("20.0")
        private Double endTime;
    }
}
