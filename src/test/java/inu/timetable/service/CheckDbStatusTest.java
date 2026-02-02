package inu.timetable.service;

import inu.timetable.repository.SubjectRepository;
import inu.timetable.repository.UserRepository;
import inu.timetable.repository.UserTimetableRepository;
import inu.timetable.repository.WishlistRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class CheckDbStatusTest {

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserTimetableRepository userTimetableRepository;

    @Autowired
    private WishlistRepository wishlistRepository;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:6543/postgres?sslmode=require&prepareThreshold=0");
        registry.add("spring.datasource.username", () -> "postgres.hrncifbbhuykffrtfind");
        registry.add("spring.datasource.password", () -> "Sns?GpJnZy@Fk5+");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Test
    void checkCurrentDbStatus() {
        System.out.println("\n=== 현재 DB 상태 ===");
        System.out.println("Users: " + userRepository.count() + "개");
        System.out.println("UserTimetables: " + userTimetableRepository.count() + "개");
        System.out.println("Wishlists: " + wishlistRepository.count() + "개");
        System.out.println("Subjects: " + subjectRepository.count() + "개");
        System.out.println("==================\n");
    }
}
