package inu.timetable.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import inu.timetable.entity.Schedule;
import inu.timetable.entity.Subject;
import inu.timetable.repository.SubjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 파싱 결과 검증 서비스
 * DB에 저장된 데이터와 새로 파싱한 데이터를 비교하여 정확도 검증
 */
@Service
@RequiredArgsConstructor
public class ParsingValidationService {

    private final PdfParseService pdfParseService;
    private final ExcelParseService excelParseService;
    private final SubjectRepository subjectRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * PDF 파싱 결과와 DB 데이터 비교
     */
    public Map<String, Object> validatePdfParsing(MultipartFile file, String semester) throws Exception {
        // 1. PDF 파싱 (DB 저장 안 함)
        List<Subject> parsedSubjects = pdfParseService.parseWithoutSaving(file);

        // 2. DB에서 동일 학기 데이터 가져오기
        List<Subject> dbSubjects = subjectRepository.findAll().stream()
            .filter(s -> semester == null || semester.isEmpty() || semester.equals(getSemesterFromSubject(s)))
            .collect(Collectors.toList());

        // 3. 비교 수행
        return compareSubjects(parsedSubjects, dbSubjects, "PDF");
    }

    /**
     * Excel 파싱 결과와 DB 데이터 비교
     */
    public Map<String, Object> validateExcelParsing(MultipartFile file, String semester) throws Exception {
        // 1. Excel 파싱 (DB 저장 안 함)
        List<Subject> parsedSubjects = excelParseService.parseWithoutSaving(file);

        // 2. DB에서 동일 학기 데이터 가져오기
        List<Subject> dbSubjects = subjectRepository.findAll().stream()
            .filter(s -> semester == null || semester.isEmpty() || semester.equals(getSemesterFromSubject(s)))
            .collect(Collectors.toList());

        // 3. 비교 수행
        return compareSubjects(parsedSubjects, dbSubjects, "Excel");
    }

    /**
     * 파싱 결과와 DB 데이터 비교
     */
    private Map<String, Object> compareSubjects(List<Subject> parsedSubjects, List<Subject> dbSubjects, String sourceType) {
        Map<String, Object> report = new HashMap<>();

        // 기본 통계
        report.put("sourceType", sourceType);
        report.put("parsedCount", parsedSubjects.size());
        report.put("dbCount", dbSubjects.size());
        report.put("timestamp", new Date().toString());

        // 과목명 기준으로 매핑
        Map<String, List<Subject>> parsedByName = parsedSubjects.stream()
            .collect(Collectors.groupingBy(Subject::getSubjectName));

        Map<String, List<Subject>> dbByName = dbSubjects.stream()
            .collect(Collectors.groupingBy(Subject::getSubjectName));

        // 1. 파싱됐지만 DB에 없는 과목 (새로 추가된 것 또는 파싱 오류)
        List<String> onlyInParsed = new ArrayList<>();
        for (String name : parsedByName.keySet()) {
            if (!dbByName.containsKey(name)) {
                onlyInParsed.add(name);
            }
        }
        report.put("onlyInParsed", onlyInParsed);
        report.put("onlyInParsedCount", onlyInParsed.size());

        // 2. DB에는 있지만 파싱 안 된 과목 (누락 또는 삭제된 과목)
        List<String> onlyInDb = new ArrayList<>();
        for (String name : dbByName.keySet()) {
            if (!parsedByName.containsKey(name)) {
                onlyInDb.add(name);
            }
        }
        report.put("onlyInDb", onlyInDb);
        report.put("onlyInDbCount", onlyInDb.size());

        // 3. 양쪽에 모두 있는 과목 중 차이점 분석
        List<Map<String, Object>> differences = new ArrayList<>();
        for (String name : parsedByName.keySet()) {
            if (dbByName.containsKey(name)) {
                List<Subject> parsedList = parsedByName.get(name);
                List<Subject> dbList = dbByName.get(name);

                // 같은 과목명이지만 교수나 시간이 다른 경우
                for (Subject parsed : parsedList) {
                    Subject bestMatch = findBestMatch(parsed, dbList);
                    if (bestMatch != null) {
                        Map<String, Object> diff = compareSubjectDetails(parsed, bestMatch);
                        if (!diff.isEmpty()) {
                            diff.put("subjectName", name);
                            differences.add(diff);
                        }
                    }
                }
            }
        }
        report.put("differences", differences);
        report.put("differenceCount", differences.size());

        // 4. 정확도 계산
        int totalSubjects = Math.max(parsedSubjects.size(), dbSubjects.size());
        if (totalSubjects > 0) {
            double accuracy = 100.0 * (totalSubjects - onlyInParsed.size() - onlyInDb.size() - differences.size()) / totalSubjects;
            report.put("accuracy", String.format("%.2f%%", accuracy));
        } else {
            report.put("accuracy", "N/A");
        }

        // 5. 요약
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalIssues", onlyInParsed.size() + onlyInDb.size() + differences.size());
        summary.put("status", differences.isEmpty() && onlyInParsed.isEmpty() && onlyInDb.isEmpty() ? "PERFECT" : "HAS_DIFFERENCES");
        report.put("summary", summary);

        return report;
    }

    /**
     * 가장 유사한 과목 찾기 (교수명 기준)
     */
    private Subject findBestMatch(Subject parsed, List<Subject> dbList) {
        for (Subject db : dbList) {
            if (Objects.equals(parsed.getProfessor(), db.getProfessor())) {
                return db;
            }
        }
        // 교수명 매칭 안 되면 첫 번째 반환
        return dbList.isEmpty() ? null : dbList.get(0);
    }

    /**
     * 과목 세부사항 비교
     */
    private Map<String, Object> compareSubjectDetails(Subject parsed, Subject db) {
        Map<String, Object> diff = new HashMap<>();

        // 학점 비교
        if (!Objects.equals(parsed.getCredits(), db.getCredits())) {
            diff.put("credits", Map.of("parsed", parsed.getCredits(), "db", db.getCredits()));
        }

        // 교수명 비교
        if (!Objects.equals(parsed.getProfessor(), db.getProfessor())) {
            diff.put("professor", Map.of("parsed", parsed.getProfessor(), "db", db.getProfessor()));
        }

        // 학과 비교
        if (!Objects.equals(parsed.getDepartment(), db.getDepartment())) {
            diff.put("department", Map.of("parsed", parsed.getDepartment(), "db", db.getDepartment()));
        }

        // 이수구분 비교
        if (!Objects.equals(parsed.getSubjectType(), db.getSubjectType())) {
            diff.put("subjectType", Map.of("parsed", parsed.getSubjectType(), "db", db.getSubjectType()));
        }

        // 야간수업 여부 비교
        if (!Objects.equals(parsed.getIsNight(), db.getIsNight())) {
            diff.put("isNight", Map.of("parsed", parsed.getIsNight(), "db", db.getIsNight()));
        }

        // 스케줄 비교
        String parsedSchedule = formatSchedules(parsed.getSchedules());
        String dbSchedule = formatSchedules(db.getSchedules());
        if (!parsedSchedule.equals(dbSchedule)) {
            diff.put("schedule", Map.of("parsed", parsedSchedule, "db", dbSchedule));
        }

        return diff;
    }

    /**
     * 스케줄을 문자열로 포맷
     */
    private String formatSchedules(List<Schedule> schedules) {
        if (schedules == null || schedules.isEmpty()) {
            return "없음";
        }
        return schedules.stream()
            .map(s -> String.format("%s %.1f-%.1f", s.getDayOfWeek(), s.getStartTime(), s.getEndTime()))
            .sorted()
            .collect(Collectors.joining(", "));
    }

    /**
     * Subject에서 학기 정보 추출 (임시 - 실제로는 Subject에 semester 필드 필요)
     */
    private String getSemesterFromSubject(Subject subject) {
        // TODO: Subject 엔티티에 semester 필드 추가 필요
        // 임시로 현재 학기로 가정
        return "2025-1";
    }
}
