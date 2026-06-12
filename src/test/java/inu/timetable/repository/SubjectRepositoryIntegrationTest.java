package inu.timetable.repository;

import inu.timetable.entity.Schedule;
import inu.timetable.entity.Subject;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
                null, null, null, null,
                null, null, null, null, null, null,
                true, ClassMethod.ONLINE, PageRequest.of(0, 10));

        assertThat(unassignedTimeIds.getContent())
                .containsExactlyInAnyOrder(unscheduledOffline.getId(), scheduledOnline.getId())
                .doesNotContain(scheduledOffline.getId());
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
}
