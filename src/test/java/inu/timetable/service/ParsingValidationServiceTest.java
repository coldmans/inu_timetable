package inu.timetable.service;

import inu.timetable.entity.Schedule;
import inu.timetable.entity.Subject;
import inu.timetable.enums.ClassMethod;
import inu.timetable.enums.SubjectType;
import inu.timetable.repository.SubjectRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParsingValidationServiceTest {

    @Mock
    private PdfParseService pdfParseService;

    @Mock
    private ExcelParseService excelParseService;

    @Mock
    private SubjectRepository subjectRepository;

    private ParsingValidationService service;

    @BeforeEach
    void setUp() {
        service = new ParsingValidationService(pdfParseService, excelParseService, subjectRepository);
    }

    @Test
    @SuppressWarnings("unchecked")
    void validatePdfParsing_reportsCountsAndDifferences() throws Exception {
        MockMultipartFile file = mockFile("timetable.pdf", "application/pdf");

        Subject parsedMismatch = subject("Data Structures", "Kim",
            schedule("Mon", 1.0, 3.0));
        Subject parsedOnly = subject("Operating Systems", "Lee",
            schedule("Wed", 2.0, 4.0));
        Subject parsedMatch = subject("Algorithms", "Park",
            schedule("Fri", 2.0, 3.0));

        Subject dbMismatch = subject("Data Structures", "Kim",
            schedule("Mon", 1.0, 2.0));
        Subject dbOnly = subject("Networks", "Choi",
            schedule("Tue", 1.0, 2.0));
        Subject dbMatch = subject("Algorithms", "Park",
            schedule("Fri", 2.0, 3.0));

        when(pdfParseService.parseWithoutSaving(file))
            .thenReturn(List.of(parsedMismatch, parsedOnly, parsedMatch));
        when(subjectRepository.findAll())
            .thenReturn(List.of(dbMismatch, dbOnly, dbMatch));

        Map<String, Object> report = service.validatePdfParsing(file, "");

        assertEquals("PDF", report.get("sourceType"));
        assertEquals(3, report.get("parsedCount"));
        assertEquals(3, report.get("dbCount"));

        List<String> onlyInParsed = (List<String>) report.get("onlyInParsed");
        assertEquals(1, onlyInParsed.size());
        assertTrue(onlyInParsed.contains("Operating Systems"));

        List<String> onlyInDb = (List<String>) report.get("onlyInDb");
        assertEquals(1, onlyInDb.size());
        assertTrue(onlyInDb.contains("Networks"));

        List<Map<String, Object>> differences = (List<Map<String, Object>>) report.get("differences");
        assertEquals(1, differences.size());
        Map<String, Object> diff = differences.get(0);
        assertEquals("Data Structures", diff.get("subjectName"));
        assertTrue(diff.containsKey("schedule"));

        Map<String, Object> scheduleDiff = (Map<String, Object>) diff.get("schedule");
        assertEquals("Mon 1.0-3.0", scheduleDiff.get("parsed"));
        assertEquals("Mon 1.0-2.0", scheduleDiff.get("db"));

        Map<String, Object> summary = (Map<String, Object>) report.get("summary");
        assertEquals("HAS_DIFFERENCES", summary.get("status"));
        assertEquals(3, summary.get("totalIssues"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void validateExcelParsing_reportsPerfectWhenAllMatched() throws Exception {
        MockMultipartFile file = mockFile("timetable.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

        Subject parsedOne = subject("Databases", "Han",
            schedule("Mon", 4.0, 5.0));
        Subject parsedTwo = subject("Computer Networks", "Choi",
            schedule("Thu", 1.0, 2.0));

        Subject dbOne = subject("Databases", "Han",
            schedule("Mon", 4.0, 5.0));
        Subject dbTwo = subject("Computer Networks", "Choi",
            schedule("Thu", 1.0, 2.0));

        when(excelParseService.parseWithoutSaving(file))
            .thenReturn(List.of(parsedOne, parsedTwo));
        when(subjectRepository.findAll())
            .thenReturn(List.of(dbOne, dbTwo));

        Map<String, Object> report = service.validateExcelParsing(file, "");

        assertEquals("Excel", report.get("sourceType"));
        assertEquals(2, report.get("parsedCount"));
        assertEquals(2, report.get("dbCount"));

        assertEquals(0, report.get("onlyInParsedCount"));
        assertEquals(0, report.get("onlyInDbCount"));
        assertEquals(0, report.get("differenceCount"));

        Map<String, Object> summary = (Map<String, Object>) report.get("summary");
        assertEquals("PERFECT", summary.get("status"));
        assertEquals(0, summary.get("totalIssues"));
    }

    private MockMultipartFile mockFile(String name, String contentType) {
        return new MockMultipartFile("file", name, contentType, new byte[] {1, 2, 3});
    }

    private Subject subject(String name, String professor, Schedule... schedules) {
        Subject subject = Subject.builder()
            .subjectName(name)
            .credits(3)
            .professor(professor)
            .isNight(false)
            .subjectType(SubjectType.전심)
            .classMethod(ClassMethod.OFFLINE)
            .grade(3)
            .department("Computer Science")
            .schedules(new ArrayList<>())
            .build();

        for (Schedule schedule : schedules) {
            schedule.setSubject(subject);
            subject.getSchedules().add(schedule);
        }
        return subject;
    }

    private Schedule schedule(String day, double start, double end) {
        return Schedule.builder()
            .dayOfWeek(day)
            .startTime(start)
            .endTime(end)
            .build();
    }
}
