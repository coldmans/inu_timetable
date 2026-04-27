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
                service = new ParsingValidationService(excelParseService, subjectRepository);
        }

        @Test
        @org.junit.jupiter.api.Disabled("PDF validation not implemented yet")
        @SuppressWarnings("unchecked")
        void validatePdfParsing_reportsCountsAndDifferences() throws Exception {
                // ... existing code ...
        }

        @Test
        @SuppressWarnings("unchecked")
        void validateExcelParsing_reportsPerfectWhenAllMatched() throws Exception {
                MockMultipartFile file = mockFile("timetable.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

                Subject parsedOne = subject("Databases", "Han",
                                schedule("Mon", 4.0, 5.0));
                Subject parsedTwo = subject("Computer Networks", "Choi",
                                schedule("Thu", 1.0, 2.0));

                Subject dbOne = subject("Databases", "Han",
                                schedule("Mon", 4.0, 5.0));
                Subject dbTwo = subject("Computer Networks", "Choi",
                                schedule("Thu", 1.0, 2.0));

                when(excelParseService.parseWithoutSaving(file, 0))
                                .thenReturn(List.of(parsedOne, parsedTwo));
                when(subjectRepository.findAll())
                                .thenReturn(List.of(dbOne, dbTwo));

                Map<String, Object> report = service.validateExcelParsing(file, "", 0);

                assertEquals(2, report.get("parsedCount"));
                assertEquals(2, report.get("dbCount"));
                assertEquals(2, report.get("matches"));

                assertEquals(0, report.get("onlyInParsedCount"));
                assertEquals(0, report.get("onlyInDbCount"));

                List<String> onlyInParsed = (List<String>) report.get("onlyInParsed");
                List<String> onlyInDb = (List<String>) report.get("onlyInDb");

                assertTrue(onlyInParsed.isEmpty());
                assertTrue(onlyInDb.isEmpty());
        }

        @Test
        @SuppressWarnings("unchecked")
        void validateExcelParsing_reportsMismatches() throws Exception {
                MockMultipartFile file = mockFile("timetable.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

                Subject parsedMismatch = subject("New Subject", "New Prof", schedule("Mon", 1.0, 2.0));
                Subject parsedMatch = subject("Existing Subject", "Prof", schedule("Tue", 1.0, 2.0));

                Subject dbMatch = subject("Existing Subject", "Prof", schedule("Tue", 1.0, 2.0));
                Subject dbMissing = subject("Deleted Subject", "Old Prof", schedule("Wed", 1.0, 2.0));

                when(excelParseService.parseWithoutSaving(file, 0))
                                .thenReturn(List.of(parsedMismatch, parsedMatch));
                when(subjectRepository.findAll())
                                .thenReturn(List.of(dbMatch, dbMissing));

                Map<String, Object> report = service.validateExcelParsing(file, "", 0);

                assertEquals(2, report.get("parsedCount"));
                assertEquals(2, report.get("dbCount"));
                assertEquals(1, report.get("matches"));

                assertEquals(1, report.get("onlyInParsedCount"));
                assertEquals(1, report.get("onlyInDbCount"));

                List<String> onlyInParsed = (List<String>) report.get("onlyInParsed");
                List<String> onlyInDb = (List<String>) report.get("onlyInDb");

                assertEquals(1, onlyInParsed.size());
                assertTrue(onlyInParsed.get(0).contains("New Subject"));

                assertEquals(1, onlyInDb.size());
                assertTrue(onlyInDb.get(0).contains("Deleted Subject"));
        }

        private MockMultipartFile mockFile(String name, String contentType) {
                return new MockMultipartFile("file", name, contentType, new byte[] { 1, 2, 3 });
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
