package inu.timetable.service;

import inu.timetable.dto.OfficialSubjectImportResponse;
import inu.timetable.entity.Schedule;
import inu.timetable.entity.Subject;
import inu.timetable.enums.ClassMethod;
import inu.timetable.enums.SubjectType;
import inu.timetable.event.SubjectDataChangedEvent;
import inu.timetable.repository.SubjectRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfficialSubjectImportServiceTest {

    @Mock
    private SubjectRepository subjectRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private OfficialSubjectImportService officialSubjectImportService;

    @BeforeEach
    void setUp() {
        officialSubjectImportService = new OfficialSubjectImportService(subjectRepository, eventPublisher);
    }

    @Test
    void previewComparesByCourseCode() throws Exception {
        Subject existing = subject("AI01001001", "기존교수", true);
        Subject removed = subject("OLD0000001", "이전교수", true);
        when(subjectRepository.findImportCandidatesBySemester("2026-1"))
                .thenReturn(List.of(existing, removed));

        OfficialSubjectImportResponse response = officialSubjectImportService.preview(sampleWorkbook(), "2026-1");

        assertThat(response.getTotalRows()).isEqualTo(2);
        assertThat(response.getAddedCount()).isEqualTo(1);
        assertThat(response.getModifiedCount()).isEqualTo(1);
        assertThat(response.getRemovedCount()).isEqualTo(1);
        assertThat(response.getUnchangedCount()).isZero();
        assertThat(response.getModifiedSubjects().get(0).getChangedFields()).contains("담당교수");
    }

    @Test
    void applyDeactivatesMissingKeyedSubjectButKeepsLegacyUnkeyed() throws Exception {
        Subject removed = subject("OLD0000001", "이전교수", true);
        Subject legacyUnkeyed = subject(null, "AI파싱교수", true);
        when(subjectRepository.findImportCandidatesBySemester("2026-1")).thenReturn(List.of(removed, legacyUnkeyed));

        OfficialSubjectImportResponse response = officialSubjectImportService.apply(sampleWorkbook(), "2026-1", true);

        // 학수번호가 있고 공식 엑셀에서 빠진 과목만 비활성화한다.
        assertThat(removed.getActive()).isFalse();
        // 학수번호 없는 레거시(PDF/AI 파싱) 과목은 공식 엑셀과 키가 겹칠 수 없으므로 비활성화하지 않는다.
        assertThat(legacyUnkeyed.getActive()).isTrue();
        assertThat(response.getRemovedCount()).isEqualTo(1);
        verify(subjectRepository).saveAll(anyList());
        verify(eventPublisher).publishEvent(any(SubjectDataChangedEvent.class));
    }

    @Test
    void applyKeepsExistingActiveWhenDeactivateMissingDisabled() throws Exception {
        Subject removed = subject("OLD0000001", "이전교수", true);
        when(subjectRepository.findImportCandidatesBySemester("2026-1")).thenReturn(List.of(removed));

        OfficialSubjectImportResponse response = officialSubjectImportService.apply(sampleWorkbook(), "2026-1", false);

        assertThat(removed.getActive()).isTrue();
        assertThat(response.getRemovedCount()).isZero();
    }

    @Test
    void applyCarriesDayAcrossRoomBlocksWithoutRepeatedDay() throws Exception {
        when(subjectRepository.findImportCandidatesBySemester("2026-1")).thenReturn(List.of());

        officialSubjectImportService.apply(sampleWorkbook(), "2026-1", true);

        ArgumentCaptor<List<Subject>> captor = ArgumentCaptor.forClass(List.class);
        verify(subjectRepository).saveAll(captor.capture());
        Subject savedSubject = captor.getValue().get(0);

        assertThat(savedSubject.getCourseCode()).isEqualTo("AI01001001");
        assertThat(savedSubject.getSchedules()).hasSize(1);
        Schedule schedule = savedSubject.getSchedules().get(0);
        assertThat(schedule.getDayOfWeek()).isEqualTo("금");
        assertThat(schedule.getStartTime()).isEqualTo(5.0);
        assertThat(schedule.getEndTime()).isEqualTo(9.0);
    }

    @Test
    void previewParsesSyllabusFormatWorkbook() throws Exception {
        when(subjectRepository.findImportCandidatesBySemester("2026-1")).thenReturn(List.of());

        OfficialSubjectImportResponse response = officialSubjectImportService.preview(syllabusWorkbook(), "2026-1");

        assertThat(response.getTotalRows()).isEqualTo(2);
        assertThat(response.getAddedCount()).isEqualTo(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void applyParsesSyllabusFormatValues() throws Exception {
        when(subjectRepository.findImportCandidatesBySemester("2026-1")).thenReturn(List.of());

        officialSubjectImportService.apply(syllabusWorkbook(), "2026-1", true);

        ArgumentCaptor<List<Subject>> captor = ArgumentCaptor.forClass(List.class);
        verify(subjectRepository).saveAll(captor.capture());
        List<Subject> saved = captor.getValue();

        Subject offline = saved.stream()
                .filter(subject -> "HS90001001".equals(subject.getCourseCode()))
                .findFirst().orElseThrow();
        // 강의계획서 학점 표기 "3.0" 은 30이 아니라 3으로 파싱되어야 한다.
        assertThat(offline.getCredits()).isEqualTo(3);
        assertThat(offline.getSubjectType()).isEqualTo(SubjectType.전심);
        assertThat(offline.getClassMethod()).isEqualTo(ClassMethod.OFFLINE);
        assertThat(offline.getSchedules()).hasSize(1);
        Schedule schedule = offline.getSchedules().get(0);
        assertThat(schedule.getDayOfWeek()).isEqualTo("목");
        assertThat(schedule.getStartTime()).isEqualTo(2.0);
        assertThat(schedule.getEndTime()).isEqualTo(5.0);

        Subject mooc = saved.stream()
                .filter(subject -> "HS90002001".equals(subject.getCourseCode()))
                .findFirst().orElseThrow();
        assertThat(mooc.getCredits()).isEqualTo(2);
        assertThat(mooc.getClassMethod()).isEqualTo(ClassMethod.ONLINE);
        assertThat(mooc.getIsNight()).isTrue();
    }

    @Test
    void previewRejectsSemesterMismatchAgainstSyllabusFile() {
        assertThatThrownBy(() -> officialSubjectImportService.preview(syllabusWorkbook(), "2026-2"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("2026-1");
    }

    private MockMultipartFile syllabusWorkbook() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("sheet1");
            // 강의계획서 조회 파일은 1행이 곧 헤더이고 시간표 컬럼명이 "시간표" 다.
            Row header = sheet.createRow(0);
            List<String> headers = List.of(
                    "0", "순번", "년도", "학기", "소속분류", "학과(부)", "이수구분", "학년", "학수번호", "교과목명",
                    "수업방법", "담당교수", "강의계획서입력여부", "시간표", "학점", "총시수", "이론", "실습",
                    "과정", "이수영역", "수업구분", "수업유형");
            for (int index = 0; index < headers.size(); index++) {
                header.createCell(index).setCellValue(headers.get(index));
            }

            createSyllabusRow(sheet, 1, "HS90001001", "포용의문화", "전공심화", "1",
                    " [ZZ-200:목(2)(3)(4)]", "3.0", "강의(이론)");
            createSyllabusRow(sheet, 2, "HS90002001", "야간무크과목", "기초교양", "2",
                    " [ZZ-200:금(야1)(야2)]", "2.0", "K-MOOC");

            workbook.write(outputStream);
            return new MockMultipartFile(
                    "file",
                    "syllabus.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    outputStream.toByteArray());
        }
    }

    private void createSyllabusRow(
            Sheet sheet, int rowIndex, String courseCode, String subjectName,
            String subjectType, String grade, String timetable, String credits, String classType) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue("0");
        row.createCell(1).setCellValue(String.valueOf(rowIndex));
        row.createCell(2).setCellValue("2026");
        row.createCell(3).setCellValue("1학기");
        row.createCell(4).setCellValue("기타");
        row.createCell(5).setCellValue("HUSS(타대학)");
        row.createCell(6).setCellValue(subjectType);
        row.createCell(7).setCellValue(grade);
        row.createCell(8).setCellValue(courseCode);
        row.createCell(9).setCellValue(subjectName);
        row.createCell(10).setCellValue("-");
        row.createCell(11).setCellValue("테스트교수");
        row.createCell(12).setCellValue("입력");
        row.createCell(13).setCellValue(timetable);
        row.createCell(14).setCellValue(credits);
        row.createCell(21).setCellValue(classType);
    }

    private MockMultipartFile sampleWorkbook() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("sheet1");
            sheet.createRow(0).createCell(0).setCellValue("2026학년도 1학기 인천대학교 학부 종합강의시간표");
            Row header = sheet.createRow(1);
            List<String> headers = List.of(
                    "순번", "대학(원)", "학과(부)", "학년", "이수구분", "이수영역", "학수번호", "교과목명",
                    "교과목명(영문)", "담당교수", "강의실", "시간표(교시)", "시간표(시간)", "교시유형", "학점",
                    "수업구분", "수업유형", "집중이수제", "성적평가", "원어강의구분");
            for (int index = 0; index < headers.size(); index++) {
                header.createCell(index).setCellValue(headers.get(index));
            }

            createSubjectRow(sheet, 2, "AI01001001", "AI에이전트", "신규교수", "컴퓨터공학부",
                    "2", "전공심화", " [05-432:금(7)(8)] [05-527:(5)(6)]", 3, "강의(이론)");
            createSubjectRow(sheet, 3, "AI01001002", "새과목", "새교수", "컴퓨터공학부",
                    "2", "전공심화", " [15-119:화(7)]", 3, "강의(이론)");

            workbook.write(outputStream);
            return new MockMultipartFile(
                    "file",
                    "official.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    outputStream.toByteArray());
        }
    }

    private void createSubjectRow(
            Sheet sheet,
            int rowIndex,
            String courseCode,
            String subjectName,
            String professor,
            String department,
            String grade,
            String subjectType,
            String time,
            int credits,
            String classMethod) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(2).setCellValue(department);
        row.createCell(3).setCellValue(grade);
        row.createCell(4).setCellValue(subjectType);
        row.createCell(6).setCellValue(courseCode);
        row.createCell(7).setCellValue(subjectName);
        row.createCell(9).setCellValue(professor);
        row.createCell(11).setCellValue(time);
        row.createCell(14).setCellValue(credits);
        row.createCell(16).setCellValue(classMethod);
    }

    private Subject subject(String courseCode, String professor, boolean active) {
        Subject subject = Subject.builder()
                .id(1L)
                .courseCode(courseCode)
                .semester("2026-1")
                .active(active)
                .subjectName("AI에이전트")
                .credits(3)
                .professor(professor)
                .department("컴퓨터공학부")
                .grade(2)
                .subjectType(SubjectType.전심)
                .classMethod(ClassMethod.OFFLINE)
                .isNight(false)
                .schedules(new ArrayList<>())
                .build();
        subject.getSchedules().add(Schedule.builder()
                .subject(subject)
                .dayOfWeek("월")
                .startTime(4.0)
                .endTime(7.0)
                .build());
        return subject;
    }
}
