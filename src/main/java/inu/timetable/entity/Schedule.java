package inu.timetable.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "schedules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Schedule {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id")
    private Subject subject;
    
    @Column(name = "day_of_week")
    private String dayOfWeek; // 월, 화, 수, 목, 금, 토, 일
    
    @Column(name = "start_time")
    private Double startTime; // 1, 1.5, 2, 2.5, etc. (A교시는 .5)
    
    @Column(name = "end_time")
    private Double endTime; // 2, 2.5, 3, 3.5, etc.
}