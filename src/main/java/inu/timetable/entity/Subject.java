package inu.timetable.entity;

import inu.timetable.enums.ClassMethod;
import inu.timetable.enums.SubjectType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "subjects", indexes = {
    @Index(name = "idx_subject_name", columnList = "subjectName"),
    @Index(name = "idx_subject_course_code", columnList = "course_code"),
    @Index(name = "idx_subject_semester_active", columnList = "semester, active"),
    @Index(name = "idx_professor", columnList = "professor"),
    @Index(name = "idx_department", columnList = "department"),
    @Index(name = "idx_grade", columnList = "grade"),
    @Index(name = "idx_subject_type", columnList = "subjectType"),
    @Index(name = "idx_is_night", columnList = "isNight"),
    @Index(name = "idx_search_filter", columnList = "subjectName, grade, department")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subject {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_code", length = 32)
    private String courseCode;

    @Column(length = 20)
    private String semester;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;
    
    @Column(nullable = false)
    private String subjectName;
    
    @Column(nullable = false)
    private Integer credits;
    
    @Column(nullable = false)
    private String professor;
    
    @OneToMany(mappedBy = "subject", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Schedule> schedules = new ArrayList<>();
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean isNight = false;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubjectType subjectType;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClassMethod classMethod;
    
    @Column
    private Integer grade;
    
    @Column
    private String department;
}
