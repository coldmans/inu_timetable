package inu.timetable.service;

import inu.timetable.entity.Schedule;
import inu.timetable.entity.Subject;
import inu.timetable.entity.User;
import inu.timetable.entity.UserNotification;
import inu.timetable.entity.UserTimetable;
import inu.timetable.enums.ClassMethod;
import inu.timetable.enums.SubjectType;
import inu.timetable.repository.UserNotificationRepository;
import inu.timetable.repository.UserTimetableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:conflict_resolution;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@Import({TimetableConflictResolutionService.class, TimetableConflictResolutionServiceTest.FixedClockConfig.class})
class TimetableConflictResolutionServiceTest {

    private static final String SEMESTER = "2026-1";

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-07-26T00:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    private TimetableConflictResolutionService timetableConflictResolutionService;

    @Autowired
    private UserTimetableRepository userTimetableRepository;

    @Autowired
    private UserNotificationRepository userNotificationRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Subject changedSubject;

    @BeforeEach
    void setUp() {
        // 시간이 '월 5-7' 로 이미 변경 반영된 과목.
        changedSubject = persistSubject("자료구조", "월", 5.0, 7.0);
    }

    @Test
    @DisplayName("새 시간이 다른 과목과 겹치는 유저는 시간표에서 제거되고 알림이 생성된다")
    void removesConflictingEntryAndCreatesNotification() {
        User conflictUser = persistUser();
        persistTimetableEntry(conflictUser, changedSubject);
        persistTimetableEntry(conflictUser, persistSubject("겹치는과목", "월", 6.0, 8.0));
        entityManager.flush();

        int removedCount = timetableConflictResolutionService
                .resolveScheduleConflicts(List.of(changedSubject), SEMESTER);
        entityManager.flush();
        entityManager.clear();

        assertThat(removedCount).isEqualTo(1);
        assertThat(userTimetableRepository.findByUserIdAndSemester(conflictUser.getId(), SEMESTER))
                .extracting(entry -> entry.getSubject().getSubjectName())
                .containsExactly("겹치는과목");
        List<UserNotification> notifications = userNotificationRepository
                .findAllByUserIdAndReadAtIsNullOrderByCreatedAtAscIdAsc(conflictUser.getId());
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getMessage()).contains("'자료구조'");
        assertThat(notifications.get(0).getReadAt()).isNull();
    }

    @Test
    @DisplayName("새 시간이 겹치지 않는 유저는 시간표가 유지되고 알림도 생성되지 않는다")
    void keepsNonConflictingEntryWithoutNotification() {
        User safeUser = persistUser();
        persistTimetableEntry(safeUser, changedSubject);
        persistTimetableEntry(safeUser, persistSubject("다른요일과목", "화", 5.0, 7.0));
        // 같은 요일이라도 끝나는 시각과 시작 시각이 맞닿기만 하면(월 7-9) 겹침이 아니다.
        persistTimetableEntry(safeUser, persistSubject("연강과목", "월", 7.0, 9.0));
        entityManager.flush();

        int removedCount = timetableConflictResolutionService
                .resolveScheduleConflicts(List.of(changedSubject), SEMESTER);
        entityManager.flush();
        entityManager.clear();

        assertThat(removedCount).isZero();
        assertThat(userTimetableRepository.findByUserIdAndSemester(safeUser.getId(), SEMESTER)).hasSize(3);
        assertThat(userNotificationRepository
                .findAllByUserIdAndReadAtIsNullOrderByCreatedAtAscIdAsc(safeUser.getId())).isEmpty();
    }

    @Test
    @DisplayName("여러 유저가 담아둔 경우 겹치는 유저만 제거되고 각자 알림을 받는다")
    void resolvesPerUserIndependently() {
        User conflictUser = persistUser();
        persistTimetableEntry(conflictUser, changedSubject);
        persistTimetableEntry(conflictUser, persistSubject("겹치는과목", "월", 4.0, 6.0));

        User safeUser = persistUser();
        persistTimetableEntry(safeUser, changedSubject);
        persistTimetableEntry(safeUser, persistSubject("안겹치는과목", "금", 1.0, 3.0));
        entityManager.flush();

        int removedCount = timetableConflictResolutionService
                .resolveScheduleConflicts(List.of(changedSubject), SEMESTER);
        entityManager.flush();
        entityManager.clear();

        assertThat(removedCount).isEqualTo(1);
        assertThat(userTimetableRepository.findByUserIdAndSemester(conflictUser.getId(), SEMESTER)).hasSize(1);
        assertThat(userTimetableRepository.findByUserIdAndSemester(safeUser.getId(), SEMESTER)).hasSize(2);
        assertThat(userNotificationRepository
                .findAllByUserIdAndReadAtIsNullOrderByCreatedAtAscIdAsc(conflictUser.getId())).hasSize(1);
        assertThat(userNotificationRepository
                .findAllByUserIdAndReadAtIsNullOrderByCreatedAtAscIdAsc(safeUser.getId())).isEmpty();
    }

    private User persistUser() {
        return entityManager.persist(User.builder()
                .username("student-" + UUID.randomUUID())
                .password("encoded-password")
                .grade(2)
                .major("컴퓨터공학부")
                .build());
    }

    private Subject persistSubject(String subjectName, String dayOfWeek, double startTime, double endTime) {
        Subject subject = Subject.builder()
                .courseCode("CC" + UUID.randomUUID().toString().substring(0, 8))
                .semester(SEMESTER)
                .active(true)
                .subjectName(subjectName)
                .credits(3)
                .professor("교수")
                .department("컴퓨터공학부")
                .grade(2)
                .subjectType(SubjectType.전심)
                .classMethod(ClassMethod.OFFLINE)
                .isNight(false)
                .build();
        subject.getSchedules().add(Schedule.builder()
                .subject(subject)
                .dayOfWeek(dayOfWeek)
                .startTime(startTime)
                .endTime(endTime)
                .build());
        return entityManager.persist(subject);
    }

    private void persistTimetableEntry(User user, Subject subject) {
        entityManager.persist(UserTimetable.builder()
                .user(user)
                .subject(subject)
                .semester(SEMESTER)
                .build());
    }
}
