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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParsingValidationServiceTest {

    @Mock
    private ExcelParseService excelParseService;

    @Mock
    private SubjectRepository subjectRepository;

    private ParsingValidationService service;

    @BeforeEach
    void setUp() {
        service = new ParsingValidationService(excelParseService, subjectRepository);
    }

    @Test
    void validatePdfParsing_notImplementedReturnsDefaultReport() {
        MockMultipartFile file = mockFile("timetable.pdf", "application/pdf");

        Map<String, Object> report = service.validatePdfParsing(file, "");

        assertEquals(0, report.get("parsedCount"));
        assertEquals(0, report.get("dbCount"));
        assertEquals("Not implemented", report.get("message"));
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

        when(excelParseService.parseWithoutSaving(file, 10))
            .thenReturn(List.of(parsedOne, parsedTwo));
        when(subjectRepository.findAll())
            .thenReturn(List.of(dbOne, dbTwo));

        Map<String, Object> report = service.validateExcelParsing(file, "", 10);

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
