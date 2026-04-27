package inu.timetable.service;

import inu.timetable.repository.SubjectRepository;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Disabled("Manual external DB verification. Requires TEST_DB_URL/TEST_DB_USERNAME/TEST_DB_PASSWORD.")
public class Verify2026DataTest {

    @Autowired
    private SubjectRepository subjectRepository;

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
    void verifyDataIs2026() {
        System.out.println("\n🔍 === 데이터 검증 시작 === 🔍");

        // 1. 전체 데이터 개수 확인
        long count = subjectRepository.count();
        System.out.println("📊 전체 과목 수: " + count);

        // 2000개 이상이어야 정상 (보통 2400~2500개)
        if (count < 1000) {
            System.err.println("❌ 경고: 데이터가 너무 적습니다! (1000개 미만)");
        } else {
            System.out.println("✅ 데이터 개수 정상 (1000개 이상)");
        }

        // 2. 2026년도 신설/특정 과목 존재 여부 확인 (로그에서 본 과목)
        boolean existsAiAgent = subjectRepository.findAll().stream()
                .anyMatch(s -> s.getSubjectName().contains("나만의AI에이전트제작"));

        if (existsAiAgent) {
            System.out.println("✅ '나만의AI에이전트제작' 과목 존재 확인 (2026년 데이터 확실함)");
        } else {
            System.err.println("❌ '나만의AI에이전트제작' 과목이 없습니다! (데이터 확인 필요)");
        }

        boolean existsSurvival = subjectRepository.findAll().stream()
                .anyMatch(s -> s.getSubjectName().contains("생존수영"));

        if (existsSurvival) {
            System.out.println("✅ '생존수영' 과목 존재 확인");
        }

        System.out.println("🔎 === 검증 종료 === 🔎\n");
    }
}
