package inu.timetable.service;

import inu.timetable.entity.Subject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Disabled("Manual external DB inspection. Requires TEST_DB_URL/TEST_DB_USERNAME/TEST_DB_PASSWORD.")
class DbContentCheckTest {

    @Autowired
    private inu.timetable.repository.SubjectRepository subjectRepository;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("TEST_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("TEST_DB_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Test
    void checkSpecificSubject() {
        System.out.println("=== DB Content Check ===");

        String[] targets = { "인문학고전읽기", "미학의이해", "환경윤리입문" };

        java.util.List<Subject> all = subjectRepository.findAll();

        for (String target : targets) {
            System.out.println("\nChecking: " + target);
            all.stream()
                    .filter(s -> s.getSubjectName().contains(target))
                    .forEach(s -> {
                        System.out.printf("Found: [%s] %s (%s) Type=%s Gr=%d\n",
                                s.getDepartment(), s.getSubjectName(), s.getProfessor(), s.getSubjectType(),
                                s.getGrade());
                    });
        }
        System.out.println("=== End Check ===");
    }
}
