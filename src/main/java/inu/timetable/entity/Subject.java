package inu.timetable.entity;

import inu.timetable.enums.ClassMethod;
import inu.timetable.enums.SubjectType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "subjects")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subject {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String subjectName;
    
    @Column(nullable = false)
    private Integer credits;
    
    @Column(nullable = false)
    private String professor;
    
    @OneToMany(mappedBy = "subject", cascade = CascadeType.ALL, orphanRemoval = true)
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