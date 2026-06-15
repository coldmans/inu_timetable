package inu.timetable.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "user_activity_daily",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_activity_daily_user_date",
                columnNames = {"user_id", "activity_date"}),
        indexes = {
                @Index(name = "idx_user_activity_daily_date", columnList = "activity_date"),
                @Index(name = "idx_user_activity_daily_user", columnList = "user_id")
        })
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserActivityDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "activity_date", nullable = false)
    private LocalDate activityDate;

    @Column(name = "first_seen_at", nullable = false)
    private LocalDateTime firstSeenAt;
}
