package inu.timetable.entity;

import inu.timetable.enums.UserStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String username;
    
    @Column(nullable = false)
    private String password; // 간단한 해시
    
    @Column
    private String nickname;
    
    @Column
    private Integer grade;
    
    @Column
    private String major; // 전공

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
    
    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<UserTimetable> timetables = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<UserMajor> userMajors = new ArrayList<>();

    public void replaceUserMajors(List<UserMajor> nextUserMajors) {
        userMajors.clear();
        nextUserMajors.forEach(this::addUserMajor);
    }

    public void addUserMajor(UserMajor userMajor) {
        userMajors.add(userMajor);
        userMajor.setUser(this);
    }

    public void withdraw(String anonymizedUsername, String anonymizedPassword, LocalDateTime withdrawnAt) {
        this.username = anonymizedUsername;
        this.password = anonymizedPassword;
        this.nickname = null;
        this.status = UserStatus.WITHDRAWN;
        this.deletedAt = withdrawnAt;
    }
}
