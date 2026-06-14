package inu.timetable.repository;

import inu.timetable.entity.Schedule;
import inu.timetable.entity.Subject;
import inu.timetable.entity.User;
import inu.timetable.entity.UserTimetable;
import inu.timetable.entity.WishlistItem;
import inu.timetable.enums.ClassMethod;
import inu.timetable.enums.SubjectType;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:subjects;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
class SubjectRepositoryIntegrationTest {

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private WishlistRepository wishlistRepository;

    @Autowired
    private UserTimetableRepository userTimetableRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findImportCandidatesBySemesterFetchesSchedulesAndLegacyUnkeyedSubjects() {
        Subject targetSubject = persistSubject("AI01001001", "2026-1", true, "월", 4.0, 7.0);
        persistSubject("AI01001002", "2026-1", false, "화", 1.0, 3.0);
        persistSubject("AI01001003", "2025-2", true, "수", 1.0, 3.0);
        persistSubject(null, null, true, "목", 1.0, 3.0);

        entityManager.flush();
        entityManager.clear();

        List<Subject> candidates = subjectRepository.findImportCandidatesBySemester("2026-1");

        assertThat(candidates)
                .extracting(Subject::getCourseCode)
                .containsExactlyInAnyOrder("AI01001001", "AI01001002", null);

        Subject loadedTarget = candidates.stream()
                .filter(subject -> subject.getCourseCode().equals(targetSubject.getCourseCode()))
                .findFirst()
                .orElseThrow();
        assertThat(loadedTarget.getSchedules()).hasSize(1);
        assertThat(loadedTarget.getSchedules().get(0).getDayOfWeek()).isEqualTo("월");
    }

    @Test
    void countByActiveTrueExcludesInactiveSubjects() {
        persistSubject("AI01001001", "2026-1", true, "월", 4.0, 7.0);
        persistSubject("AI01001002", "2026-1", false, "화", 1.0, 3.0);

        entityManager.flush();

        assertThat(subjectRepository.countByActiveTrue()).isEqualTo(1);
    }

    @Test
    void findDistinctDepartmentsExcludesInactiveSubjects() {
        Subject inactive = persistSubject("AI01001001", "2026-1", false, "월", 4.0, 7.0);
        inactive.setDepartment("폐과");
        Subject active = persistSubject("AI01001002", "2026-1", true, "화", 1.0, 3.0);
        active.setDepartment("컴퓨터공학부");

        entityManager.flush();

        assertThat(subjectRepository.findDistinctDepartments())
                .containsExactly("컴퓨터공학부");
    }

    @Test
    void findIdsWithFiltersCanFindOnlineAndUnscheduledSubjects() {
        Subject scheduledOffline = persistSubject("AI01001001", "2026-1", true, "월", 4.0, 7.0);
        Subject unscheduledOffline = persistSubject("AI01001002", "2026-1", true, null, null, null);
        Subject scheduledOnline = persistSubject("AI01001003", "2026-1", true, "화", 1.0, 3.0);
        scheduledOnline.setClassMethod(ClassMethod.ONLINE);

        entityManager.flush();
        entityManager.clear();

        Page<Long> unassignedTimeIds = subjectRepository.findIdsWithFilters(
                null, null, null, Collections.singletonList("__unused_department__"), 0, null,
                null, null, null, null, null, null,
                true, ClassMethod.ONLINE, PageRequest.of(0, 10));

        assertThat(unassignedTimeIds.getContent())
                .containsExactlyInAnyOrder(unscheduledOffline.getId(), scheduledOnline.getId())
                .doesNotContain(scheduledOffline.getId());
    }

    @Test
    void findIdsWithFiltersCanFilterByMultipleDepartments() {
        Subject computer = persistSubject("AI01001001", "2026-1", true, "월", 4.0, 7.0);
        computer.setDepartment("컴퓨터공학부");
        Subject embedded = persistSubject("AI01001002", "2026-1", true, "화", 1.0, 3.0);
        embedded.setDepartment("임베디드시스템공학과");
        Subject math = persistSubject("AI01001003", "2026-1", true, "수", 1.0, 3.0);
        math.setDepartment("수학과");

        entityManager.flush();
        entityManager.clear();

        Page<Long> subjectIds = subjectRepository.findIdsWithFilters(
                null, null, null, Arrays.asList("컴퓨터공학부", "임베디드시스템공학과"), 2, null,
                null, null, null, null, null, null,
                null, ClassMethod.ONLINE, PageRequest.of(0, 10));

        assertThat(subjectIds.getContent())
                .containsExactlyInAnyOrder(computer.getId(), embedded.getId())
                .doesNotContain(math.getId());
    }

    @Test
    void countBySubjectIdsReturnsWishlistCountsForSubjects() {
        Subject popular = persistSubject("AI01001001", "2026-1", true, "월", 4.0, 7.0);
        Subject quiet = persistSubject("AI01001002", "2026-1", true, "화", 1.0, 3.0);
        Subject other = persistSubject("AI01001003", "2026-1", true, "수", 1.0, 3.0);
        User firstUser = persistUser();
        User secondUser = persistUser();
        User thirdUser = persistUser();
        persistWishlistItem(firstUser, popular);
        persistWishlistItem(secondUser, popular);
        persistWishlistItem(thirdUser, other);

        entityManager.flush();
        entityManager.clear();

        List<WishlistRepository.SubjectWishlistCount> counts = wishlistRepository.countBySubjectIds(
                List.of(popular.getId(), quiet.getId()));

        assertThat(counts)
                .extracting(
                        WishlistRepository.SubjectWishlistCount::getSubjectId,
                        WishlistRepository.SubjectWishlistCount::getWishlistCount)
                .containsExactly(tuple(popular.getId(), 2L));
    }

    @Test
    void countAddedUsersBySubjectIdsReturnsTimetableAddCountsForSubjects() {
        Subject popular = persistSubject("AI01001004", "2026-1", true, "월", 4.0, 7.0);
        Subject quiet = persistSubject("AI01001005", "2026-1", true, "화", 1.0, 3.0);
        Subject other = persistSubject("AI01001006", "2026-1", true, "수", 1.0, 3.0);
        User firstUser = persistUser();
        User secondUser = persistUser();
        User thirdUser = persistUser();
        persistUserTimetable(firstUser, popular);
        persistUserTimetable(secondUser, popular);
        persistUserTimetable(thirdUser, other);

        entityManager.flush();
        entityManager.clear();

        List<UserTimetableRepository.SubjectTimetableAddCount> counts =
                userTimetableRepository.countAddedUsersBySubjectIds(List.of(popular.getId(), quiet.getId()));

        assertThat(counts)
                .extracting(
                        UserTimetableRepository.SubjectTimetableAddCount::getSubjectId,
                        UserTimetableRepository.SubjectTimetableAddCount::getTimetableAddCount)
                .containsExactly(tuple(popular.getId(), 2L));
    }

    @Test
    void findIdsWithFiltersOrdersByTimetableAddCountDescending() {
        Subject quiet = persistSubject("AI01001001", "2026-1", true, "월", 4.0, 7.0);
        Subject popular = persistSubject("AI01001002", "2026-1", true, "화", 1.0, 3.0);
        Subject multiSchedule = persistSubject("AI01001003", "2026-1", true, "수", 1.0, 3.0);
        persistSchedule(multiSchedule, "목", 4.0, 6.0);

        User firstUser = persistUser();
        User secondUser = persistUser();
        User thirdUser = persistUser();
        persistUserTimetable(firstUser, popular);
        persistUserTimetable(secondUser, popular);
        persistUserTimetable(thirdUser, multiSchedule);

        entityManager.flush();
        entityManager.clear();

        Page<Long> subjectIds = subjectRepository.findIdsWithFilters(
                null, null, null, Collections.singletonList("__unused_department__"), 0, null,
                null, null, null, null, null, null,
                null, ClassMethod.ONLINE, PageRequest.of(0, 10));

        assertThat(subjectIds.getContent())
                .containsExactly(popular.getId(), multiSchedule.getId(), quiet.getId());
    }

    private Subject persistSubject(
            String courseCode,
            String semester,
            boolean active,
            String day,
            Double start,
            Double end) {
        Subject subject = Subject.builder()
                .courseCode(courseCode)
                .semester(semester)
                .active(active)
                .subjectName("테스트과목 " + (courseCode == null ? "unkeyed" : courseCode))
                .credits(3)
                .professor("교수")
                .department("컴퓨터공학부")
                .grade(2)
                .subjectType(SubjectType.전심)
                .classMethod(ClassMethod.OFFLINE)
                .isNight(false)
                .schedules(new ArrayList<>())
                .build();
        if (day != null) {
            subject.getSchedules().add(Schedule.builder()
                    .subject(subject)
                    .dayOfWeek(day)
                    .startTime(start)
                    .endTime(end)
                    .build());
        }
        return entityManager.persistAndFlush(subject);
    }

    private Schedule persistSchedule(Subject subject, String day, Double start, Double end) {
        Schedule schedule = Schedule.builder()
                .subject(subject)
                .dayOfWeek(day)
                .startTime(start)
                .endTime(end)
                .build();
        return entityManager.persistAndFlush(schedule);
    }

    private User persistUser() {
        User user = User.builder()
                .username("student-" + UUID.randomUUID())
                .password("password")
                .grade(2)
                .major("컴퓨터공학부")
                .build();
        return entityManager.persistAndFlush(user);
    }

    private WishlistItem persistWishlistItem(User user, Subject subject) {
        WishlistItem item = WishlistItem.builder()
                .user(user)
                .subject(subject)
                .semester("2026-1")
                .priority(1)
                .build();
        return entityManager.persistAndFlush(item);
    }

    private UserTimetable persistUserTimetable(User user, Subject subject) {
        UserTimetable item = UserTimetable.builder()
                .user(user)
                .subject(subject)
                .semester("2026-1")
                .build();
        return entityManager.persistAndFlush(item);
    }
}
