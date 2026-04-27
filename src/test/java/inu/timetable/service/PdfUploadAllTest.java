package inu.timetable.service;

import inu.timetable.entity.Subject;
import inu.timetable.repository.SubjectRepository;
import inu.timetable.repository.UserTimetableRepository;
import inu.timetable.repository.WishlistRepository;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * DB 초기화 후 모든 PDF 파싱하여 저장하는 테스트
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Disabled("Destructive manual import test. Requires explicit TEST_DB_* and TEST_GEMINI_API_KEY env vars.")
class PdfUploadAllTest {

    @Autowired
    private PdfParseService pdfParseService;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private WishlistRepository wishlistRepository;

    @Autowired
    private UserTimetableRepository userTimetableRepository;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("gemini.api.key", () -> System.getenv("TEST_GEMINI_API_KEY"));

        // Real DB Connection
        registry.add("spring.datasource.url", () -> System.getenv("TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("TEST_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("TEST_DB_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Test
    void deleteAllAndUploadAllPdfs() throws Exception {
        Path dir = Paths.get("src/main/resources/pdf");
        if (!Files.exists(dir)) {
            System.err.println("PDF 디렉토리를 찾을 수 없습니다: " + dir.toAbsolutePath());
            return;
        }

        // 1. 기존 데이터 삭제 (외래키 순서: user_timetables -> wishlist -> subjects)
        System.out.println("=== 기존 데이터 삭제 시작 ===");
        long timetableCount = userTimetableRepository.count();
        long wishlistCount = wishlistRepository.count();
        long subjectCount = subjectRepository.count();
        System.out.println("삭제 전 - UserTimetable: " + timetableCount + "개, Wishlist: " + wishlistCount + "개, Subject: " + subjectCount + "개");

        userTimetableRepository.deleteAll();
        System.out.println("UserTimetable 삭제 완료");

        wishlistRepository.deleteAll();
        System.out.println("Wishlist 삭제 완료");

        subjectRepository.deleteAll();
        System.out.println("Subject 삭제 완료");

        // 2. PDF 파일 목록 조회
        List<Path> pdfFiles = Files.list(dir)
                .filter(p -> p.toString().endsWith(".pdf"))
                .sorted()
                .toList();

        System.out.println("\n=== PDF 파일 목록 ===");
        System.out.println("총 " + pdfFiles.size() + "개 파일");
        pdfFiles.forEach(p -> System.out.println("- " + p.getFileName()));

        // 3. PDF 파일별로 파싱 및 저장 (DB 연결 타임아웃 방지)
        int totalSaved = 0;

        for (int i = 0; i < pdfFiles.size(); i++) {
            Path path = pdfFiles.get(i);
            System.out.println("\n=== [" + (i + 1) + "/" + pdfFiles.size() + "] " + path.getFileName() + " 파싱 시작 ===");

            try {
                MockMultipartFile file = new MockMultipartFile(
                        "file",
                        path.getFileName().toString(),
                        "application/pdf",
                        Files.newInputStream(path));

                List<Subject> subjects = pdfParseService.parseWithoutSaving(file);
                System.out.println(">> 파싱 완료: " + subjects.size() + "개 과목 추출");

                // 즉시 DB에 저장
                if (!subjects.isEmpty()) {
                    List<Subject> saved = subjectRepository.saveAll(subjects);
                    totalSaved += saved.size();
                    System.out.println(">> DB 저장 완료: " + saved.size() + "개 (누적: " + totalSaved + "개)");
                }

                // API Rate Limit 방지를 위한 대기 (마지막 파일 제외)
                if (i < pdfFiles.size() - 1) {
                    System.out.println("다음 파일 처리를 위해 3초 대기...");
                    Thread.sleep(3000);
                }
            } catch (Exception e) {
                System.err.println("!! 파싱/저장 실패: " + path.getFileName());
                e.printStackTrace();
            }
        }

        System.out.println("\n=== 전체 저장 완료: " + totalSaved + "개 ===");

        // 5. 결과 확인
        System.out.println("\n=== 최종 결과 ===");
        System.out.println("DB 총 과목 수: " + subjectRepository.count());
    }
}
