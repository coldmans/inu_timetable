package inu.timetable.service;

import inu.timetable.entity.Subject;
import inu.timetable.repository.SubjectRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ParsingValidationService {

    private final ExcelParseService excelParseService;
    private final SubjectRepository subjectRepository;

    public Map<String, Object> validatePdfParsing(MultipartFile file, String semester) {
        Map<String, Object> report = new HashMap<>();
        report.put("parsedCount", 0);
        report.put("dbCount", 0);
        report.put("message", "Not implemented");
        return report;
    }

    public Map<String, Object> validateExcelParsing(MultipartFile file, String semester, int maxChunks)
            throws IOException {
        Map<String, Object> report = new HashMap<>();

        // 1. Excel 파싱 (DB 저장 없이)
        List<Subject> excelSubjects = excelParseService.parseWithoutSaving(file, maxChunks);

        // 2. DB 조회
        List<Subject> dbSubjects = subjectRepository.findAll();

        // 3. 비교 로직
        List<String> onlyInParsed = new ArrayList<>();
        List<String> onlyInDb = new ArrayList<>();
        int matchCount = 0;

        // 비교를 위한 Key 생성 함수
        Function<Subject, String> keyGenerator = s -> String.format("%s|%s|%s|%d|%s",
                s.getSubjectName().trim(),
                s.getProfessor() != null ? s.getProfessor().trim() : "미배정",
                s.getDepartment() != null ? s.getDepartment().trim() : "미분류",
                s.getGrade() != null ? s.getGrade() : 0,
                s.getSubjectType() != null ? s.getSubjectType().name() : "일선");

        Set<String> dbKeys = dbSubjects.stream()
                .map(keyGenerator)
                .collect(Collectors.toSet());

        Set<String> excelKeys = excelSubjects.stream()
                .map(keyGenerator)
                .collect(Collectors.toSet());

        // Excel에 있는데 DB에 없는 것 찾기 (Parsed but not in DB)
        for (Subject s : excelSubjects) {
            String key = keyGenerator.apply(s);
            if (dbKeys.contains(key)) {
                matchCount++;
            } else {
                onlyInParsed.add(formatSubjectInfo(s));
            }
        }

        // DB에 있는데 Excel에 없는 것 찾기 (In DB but not Parsed)
        for (Subject s : dbSubjects) {
            String key = keyGenerator.apply(s);
            if (!excelKeys.contains(key)) {
                onlyInDb.add(formatSubjectInfo(s));
            }
        }

        int totalIssues = onlyInParsed.size() + onlyInDb.size();
        String status = totalIssues == 0 ? "PERFECT" : "HAS_DIFFERENCES";

        double accuracy = 0.0;
        if (!excelSubjects.isEmpty()) {
            accuracy = (double) matchCount / excelSubjects.size() * 100.0;
        }

        report.put("sourceType", "Excel");
        report.put("parsedCount", excelSubjects.size());
        report.put("dbCount", dbSubjects.size());
        report.put("matches", matchCount);
        report.put("accuracy", String.format("%.1f%%", accuracy));

        report.put("onlyInParsed", onlyInParsed);
        report.put("onlyInParsedCount", onlyInParsed.size());

        report.put("onlyInDb", onlyInDb);
        report.put("onlyInDbCount", onlyInDb.size());

        // 필드 불일치 (Time/Schedule Check)
        List<String> differences = new ArrayList<>();
        int mismatchCount = 0;

        for (Subject excelSubject : excelSubjects) {
            String key = keyGenerator.apply(excelSubject);
            if (dbKeys.contains(key)) {
                // Find corresponding DB subject (This is inefficient O(N), but acceptable for
                // ad-hoc validation of ~2000 items)
                Subject dbSubject = dbSubjects.stream()
                        .filter(s -> keyGenerator.apply(s).equals(key))
                        .findFirst()
                        .orElse(null);

                if (dbSubject != null) {
                    String excelTime = formatSchedule(excelSubject.getSchedules());
                    String dbTime = formatSchedule(dbSubject.getSchedules());

                    if (!excelTime.equals(dbTime)) {
                        differences.add(String.format("[%s] %s (%s) - Excel: %s vs DB: %s",
                                excelSubject.getDepartment(), excelSubject.getSubjectName(),
                                excelSubject.getProfessor(),
                                excelTime, dbTime));
                    }
                }
            }
        }

        report.put("differences", differences);
        report.put("differenceCount", differences.size());

        Map<String, Object> summary = new HashMap<>();
        summary.put("status", status);
        summary.put("totalIssues", totalIssues);
        report.put("summary", summary);

        return report;
    }

    private String formatSubjectInfo(Subject s) {
        return String.format("[%s] %s (%s, %d학년) - %s",
                s.getDepartment(), s.getSubjectName(), s.getProfessor(), s.getGrade(), s.getSubjectType());
    }

    private String formatSchedule(List<inu.timetable.entity.Schedule> schedules) {
        if (schedules == null || schedules.isEmpty())
            return "미지정";
        return schedules.stream()
                .sorted(Comparator.comparing(inu.timetable.entity.Schedule::getDayOfWeek)
                        .thenComparing(inu.timetable.entity.Schedule::getStartTime))
                .map(s -> String.format("%s %.1f-%.1f", s.getDayOfWeek(), s.getStartTime(), s.getEndTime()))
                .collect(Collectors.joining(", "));
    }

    // 제외할 과목 목록
    private static final Set<String> EXCLUDED_SUBJECTS = Set.of(
        "대학영어회화1",
        "AI사고와데이터리터러시",
        "AI시대의글쓰기이론과실제",
        "Academic English"
    );

    /**
     * 엑셀 파일(리소스)과 DB를 비교하여 누락된 과목 분석
     */
    public Map<String, Object> analyzeMissingSubjects() {
        Map<String, Object> result = new HashMap<>();

        try {
            // 1. 엑셀 파일에서 과목 추출
            List<ExcelSubjectInfo> excelSubjects = extractSubjectsFromExcel();
            result.put("excelTotalCount", excelSubjects.size());

            // 제외 과목 필터링
            List<ExcelSubjectInfo> filteredExcel = excelSubjects.stream()
                .filter(s -> !EXCLUDED_SUBJECTS.contains(s.subjectName.trim()))
                .toList();
            result.put("excelFilteredCount", filteredExcel.size());
            result.put("excludedSubjects", EXCLUDED_SUBJECTS);

            // 2. DB에서 과목 조회
            List<Subject> dbSubjects = subjectRepository.findAll();
            result.put("dbCount", dbSubjects.size());

            // 3. DB 과목명 세트 (정규화)
            Set<String> dbSubjectNames = dbSubjects.stream()
                .map(s -> normalizeSubjectName(s.getSubjectName()))
                .collect(Collectors.toSet());

            // 4. 엑셀에는 있지만 DB에 없는 과목
            List<ExcelSubjectInfo> missingInDb = filteredExcel.stream()
                .filter(s -> !dbSubjectNames.contains(normalizeSubjectName(s.subjectName)))
                .toList();

            result.put("missingCount", missingInDb.size());

            // 학과별 그룹핑
            Map<String, List<Map<String, String>>> byDepartment = new LinkedHashMap<>();
            missingInDb.stream()
                .collect(Collectors.groupingBy(s -> s.department != null && !s.department.isBlank() ? s.department : "미분류"))
                .forEach((dept, subjects) -> {
                    List<Map<String, String>> subjectList = subjects.stream()
                        .map(s -> {
                            Map<String, String> info = new LinkedHashMap<>();
                            info.put("subjectName", s.subjectName);
                            info.put("professor", s.professor);
                            info.put("subjectType", s.subjectType);
                            info.put("grade", s.grade);
                            info.put("timeSchedule", s.timeSchedule);
                            info.put("classMethod", s.classMethod);
                            info.put("credits", s.credits);
                            return info;
                        })
                        .toList();
                    byDepartment.put(dept, subjectList);
                });
            result.put("missingByDepartment", byDepartment);

            // 5. 누락 원인 분석
            Map<String, Integer> reasonCounts = analyzeMissingReasons(missingInDb);
            result.put("missingReasons", reasonCounts);

            // 6. 원본 데이터 (삽입용)
            List<Map<String, String>> rawMissingData = missingInDb.stream()
                .map(s -> {
                    Map<String, String> info = new LinkedHashMap<>();
                    info.put("rowNum", String.valueOf(s.rowNum));
                    info.put("department", s.department);
                    info.put("subjectName", s.subjectName);
                    info.put("professor", s.professor);
                    info.put("subjectType", s.subjectType);
                    info.put("grade", s.grade);
                    info.put("timeSchedule", s.timeSchedule);
                    info.put("credits", s.credits);
                    info.put("classMethod", s.classMethod);
                    return info;
                })
                .toList();
            result.put("rawMissingData", rawMissingData);

        } catch (Exception e) {
            result.put("error", e.getMessage());
            e.printStackTrace();
        }

        return result;
    }

    /**
     * 엑셀 헤더 정보 반환 (컬럼 구조 확인용)
     */
    public Map<String, Object> getExcelHeaders() {
        Map<String, Object> result = new HashMap<>();

        try {
            ClassPathResource resource = new ClassPathResource("excel/강의계획서조회_20260202_000046.xlsx");

            try (InputStream is = resource.getInputStream();
                 Workbook workbook = new XSSFWorkbook(is)) {

                Sheet sheet = workbook.getSheetAt(0);
                Row headerRow = sheet.getRow(0);

                List<String> headers = new ArrayList<>();
                for (int i = 0; i < 20; i++) {
                    Cell cell = headerRow.getCell(i);
                    headers.add("[" + i + "] " + getCellValue(cell));
                }
                result.put("headers", headers);

                // 첫 3행 샘플 데이터
                List<List<String>> sampleRows = new ArrayList<>();
                for (int rowNum = 1; rowNum <= 3; rowNum++) {
                    Row row = sheet.getRow(rowNum);
                    if (row != null) {
                        List<String> rowData = new ArrayList<>();
                        for (int i = 0; i < 20; i++) {
                            rowData.add(getCellValue(row.getCell(i)));
                        }
                        sampleRows.add(rowData);
                    }
                }
                result.put("sampleRows", sampleRows);
            }
        } catch (Exception e) {
            result.put("error", e.getMessage());
        }

        return result;
    }

    private List<ExcelSubjectInfo> extractSubjectsFromExcel() throws Exception {
        List<ExcelSubjectInfo> subjects = new ArrayList<>();

        ClassPathResource resource = new ClassPathResource("excel/강의계획서조회_20260202_000046.xlsx");

        try (InputStream is = resource.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            int totalRows = sheet.getLastRowNum();

            // 헤더 행 확인해서 컬럼 인덱스 매핑
            Row headerRow = sheet.getRow(0);
            Map<String, Integer> columnIndex = new HashMap<>();
            for (int i = 0; i < 20; i++) {
                String header = getCellValue(headerRow.getCell(i));
                if (!header.isBlank()) {
                    columnIndex.put(header, i);
                }
            }

            // 컬럼 인덱스 (실제 엑셀 구조 기반)
            // [5] 학과(부), [6] 이수구분, [7] 학년, [9] 교과목명, [10] 수업방법, [11] 담당교수, [13] 시간표, [14] 학점
            int colCollege = columnIndex.getOrDefault("소속분류", 4);
            int colDepartment = columnIndex.getOrDefault("학과(부)", 5);
            int colSubjectType = columnIndex.getOrDefault("이수구분", 6);
            int colGrade = columnIndex.getOrDefault("학년", 7);
            int colSubjectCode = columnIndex.getOrDefault("학수번호", 8);
            int colSubjectName = columnIndex.getOrDefault("교과목명", 9);
            int colClassMethod = columnIndex.getOrDefault("수업방법", 10);
            int colProfessor = columnIndex.getOrDefault("담당교수", 11);
            int colTimeSchedule = columnIndex.getOrDefault("시간표", 13);
            int colCredits = columnIndex.getOrDefault("학점", 14);
            int colRoom = 13; // Not used but kept for compatibility
            int colClassType = 10; // Same as classMethod

            // 데이터 파싱 (1행부터)
            for (int rowNum = 1; rowNum <= totalRows; rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null) continue;

                ExcelSubjectInfo info = new ExcelSubjectInfo();
                info.rowNum = rowNum;
                info.college = getCellValue(row.getCell(colCollege));
                info.department = getCellValue(row.getCell(colDepartment));
                info.grade = getCellValue(row.getCell(colGrade));
                info.subjectType = getCellValue(row.getCell(colSubjectType));
                info.subjectCode = getCellValue(row.getCell(colSubjectCode));
                info.subjectName = getCellValue(row.getCell(colSubjectName));
                info.professor = getCellValue(row.getCell(colProfessor));
                info.room = getCellValue(row.getCell(colRoom));
                info.timeSchedule = getCellValue(row.getCell(colTimeSchedule));
                info.credits = getCellValue(row.getCell(colCredits));
                info.classType = getCellValue(row.getCell(colClassType));
                info.classMethod = getCellValue(row.getCell(colClassMethod));

                if (info.subjectName != null && !info.subjectName.isBlank()) {
                    subjects.add(info);
                }
            }
        }

        return subjects;
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return "";

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getDateCellValue().toString();
                } else {
                    double val = cell.getNumericCellValue();
                    if (val == (int) val) {
                        yield String.valueOf((int) val);
                    } else {
                        yield String.valueOf(val);
                    }
                }
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue();
                } catch (Exception e) {
                    yield String.valueOf(cell.getNumericCellValue());
                }
            }
            default -> "";
        };
    }

    private String normalizeSubjectName(String name) {
        if (name == null) return "";
        return name.trim().replaceAll("\\s+", "");
    }

    private Map<String, Integer> analyzeMissingReasons(List<ExcelSubjectInfo> missingSubjects) {
        Map<String, Integer> reasonCounts = new LinkedHashMap<>();

        for (ExcelSubjectInfo s : missingSubjects) {
            List<String> reasons = new ArrayList<>();

            // 1. 시간표가 비어있는 경우
            if (s.timeSchedule == null || s.timeSchedule.isBlank()) {
                reasons.add("시간표_없음");
            }

            // 2. e-Learning/온라인 과목
            if (s.classMethod != null &&
                (s.classMethod.contains("e-Learning") ||
                 s.classMethod.contains("온라인") ||
                 s.classMethod.contains("비대면"))) {
                reasons.add("온라인_과목");
            }

            // 3. 특수 시간표 형식
            if (s.timeSchedule != null) {
                if (s.timeSchedule.contains("별도") || s.timeSchedule.contains("협의")) {
                    reasons.add("별도협의_시간");
                }
                if (s.timeSchedule.contains("집중") || s.timeSchedule.contains("방학")) {
                    reasons.add("집중이수");
                }
            }

            // 4. 담당교수가 비어있는 경우
            if (s.professor == null || s.professor.isBlank()) {
                reasons.add("교수_미배정");
            }

            if (reasons.isEmpty()) {
                reasons.add("AI_파싱_누락");
            }

            for (String reason : reasons) {
                reasonCounts.merge(reason, 1, Integer::sum);
            }
        }

        return reasonCounts;
    }

    // 엑셀 과목 정보 저장용 내부 클래스
    static class ExcelSubjectInfo {
        int rowNum;
        String college;
        String department;
        String grade;
        String subjectType;
        String subjectCode;
        String subjectName;
        String professor;
        String room;
        String timeSchedule;
        String credits;
        String classType;
        String classMethod;
    }

    /**
     * 누락된 과목을 DB에 추가
     */
    @org.springframework.transaction.annotation.Transactional
    public Map<String, Object> insertMissingSubjects() {
        Map<String, Object> result = new HashMap<>();

        try {
            // 1. 엑셀에서 과목 추출
            List<ExcelSubjectInfo> excelSubjects = extractSubjectsFromExcel();

            // 제외 과목 필터링
            List<ExcelSubjectInfo> filteredExcel = excelSubjects.stream()
                .filter(s -> !EXCLUDED_SUBJECTS.contains(s.subjectName.trim()))
                .toList();

            // 2. DB 과목명 세트
            List<Subject> dbSubjects = subjectRepository.findAll();
            Set<String> dbSubjectNames = dbSubjects.stream()
                .map(s -> normalizeSubjectName(s.getSubjectName()))
                .collect(Collectors.toSet());

            // 3. 누락 과목 찾기
            List<ExcelSubjectInfo> missingSubjects = filteredExcel.stream()
                .filter(s -> !dbSubjectNames.contains(normalizeSubjectName(s.subjectName)))
                .toList();

            // 4. Subject 엔티티로 변환 후 저장
            List<Subject> subjectsToSave = new ArrayList<>();
            List<String> parseErrors = new ArrayList<>();

            for (ExcelSubjectInfo info : missingSubjects) {
                try {
                    Subject subject = convertToSubject(info);
                    if (subject != null) {
                        subjectsToSave.add(subject);
                    }
                } catch (Exception e) {
                    parseErrors.add(info.subjectName + ": " + e.getMessage());
                }
            }

            // 5. DB 저장
            List<Subject> savedSubjects = subjectRepository.saveAll(subjectsToSave);

            result.put("success", true);
            result.put("totalMissing", missingSubjects.size());
            result.put("savedCount", savedSubjects.size());
            result.put("parseErrors", parseErrors);

            // 저장된 과목 목록
            List<String> savedNames = savedSubjects.stream()
                .map(s -> s.getSubjectName() + " (" + s.getProfessor() + ")")
                .toList();
            result.put("savedSubjects", savedNames);

        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            e.printStackTrace();
        }

        return result;
    }

    private Subject convertToSubject(ExcelSubjectInfo info) {
        // 시간표 파싱
        List<inu.timetable.entity.Schedule> schedules = parseTimeSchedule(info.timeSchedule);

        // 학년 파싱
        Integer grade = null;
        if (info.grade != null && !info.grade.isBlank()) {
            try {
                grade = Integer.parseInt(info.grade.replaceAll("[^0-9]", ""));
                if (grade < 1 || grade > 4) grade = null;
            } catch (NumberFormatException e) {
                grade = null;
            }
        }

        // 학점 파싱
        int credits = 3; // 기본값
        if (info.credits != null && !info.credits.isBlank()) {
            try {
                credits = Integer.parseInt(info.credits.replaceAll("[^0-9]", ""));
            } catch (NumberFormatException e) {
                credits = 3;
            }
        }

        // 이수구분 파싱
        inu.timetable.enums.SubjectType subjectType = parseSubjectTypeEnum(info.subjectType);

        // 수업방법 파싱
        inu.timetable.enums.ClassMethod classMethod = parseClassMethodEnum(info.classMethod);

        // 야간 여부
        boolean isNight = info.timeSchedule != null && info.timeSchedule.contains("야");

        Subject subject = Subject.builder()
            .subjectName(info.subjectName)
            .credits(credits)
            .professor(info.professor != null && !info.professor.isBlank() ? info.professor : "미배정")
            .isNight(isNight)
            .subjectType(subjectType)
            .classMethod(classMethod)
            .grade(grade)
            .department(info.department != null && !info.department.isBlank() ? info.department : "미분류")
            .schedules(new ArrayList<>())
            .build();

        for (inu.timetable.entity.Schedule schedule : schedules) {
            schedule.setSubject(subject);
            subject.getSchedules().add(schedule);
        }

        return subject;
    }

    /**
     * 시간표 문자열 파싱 (엑셀 원본 형식)
     * 예: [15-116:화(1),금(1)] or [15-113:화(2B-3)] [15-317:금(2B-3)]
     */
    private List<inu.timetable.entity.Schedule> parseTimeSchedule(String timeSchedule) {
        List<inu.timetable.entity.Schedule> schedules = new ArrayList<>();

        if (timeSchedule == null || timeSchedule.isBlank() || "-".equals(timeSchedule.trim())) {
            return schedules;
        }

        // [강의실:요일(교시)] 패턴 추출
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "\\[([^:]+):([^\\]]+)\\]"
        );
        java.util.regex.Matcher matcher = pattern.matcher(timeSchedule);

        while (matcher.find()) {
            String timeInfo = matcher.group(2); // 요일(교시) 부분

            // 쉼표로 구분된 여러 요일/시간 처리
            String[] parts = timeInfo.split(",");
            for (String part : parts) {
                part = part.trim();
                if (part.isEmpty()) continue;

                // 요일 추출
                String day = null;
                for (String d : List.of("월", "화", "수", "목", "금", "토", "일")) {
                    if (part.startsWith(d)) {
                        day = d;
                        part = part.substring(1);
                        break;
                    }
                }

                if (day == null) continue;

                // 교시 파싱: (1), (2B-3), (7-8A)(8B-9), (야1-야2) 등
                java.util.regex.Pattern periodPattern = java.util.regex.Pattern.compile(
                    "\\(([^)]+)\\)"
                );
                java.util.regex.Matcher periodMatcher = periodPattern.matcher(part);

                List<double[]> timeRanges = new ArrayList<>();
                while (periodMatcher.find()) {
                    String period = periodMatcher.group(1);
                    double[] range = parsePeriod(period);
                    if (range != null) {
                        timeRanges.add(range);
                    }
                }

                // 시간 범위 병합 및 Schedule 생성
                for (double[] range : timeRanges) {
                    schedules.add(inu.timetable.entity.Schedule.builder()
                        .dayOfWeek(day)
                        .startTime(range[0])
                        .endTime(range[1])
                        .build());
                }
            }
        }

        // 같은 요일의 연속된 시간 병합
        return mergeSchedules(schedules);
    }

    /**
     * 교시 문자열 파싱
     * 예: "1", "2B-3", "7-8A", "야1-야2"
     */
    private double[] parsePeriod(String period) {
        if (period == null || period.isBlank()) return null;

        boolean isNight = period.contains("야");
        period = period.replace("야", "");

        double start, end;

        if (period.contains("-")) {
            String[] parts = period.split("-");
            start = parseTimeValue(parts[0]);
            end = parseTimeValue(parts[1]);

            // 종료 시간 계산
            if (parts[1].contains("A") || parts[1].contains("B")) {
                end += 0.5;
            } else {
                end += 1.0;
            }
        } else {
            start = parseTimeValue(period);
            if (period.contains("A") || period.contains("B")) {
                end = start + 0.5;
            } else {
                end = start + 1.0;
            }
        }

        if (isNight) {
            start += 9.0;
            end += 9.0;
        }

        return new double[]{start, end};
    }

    private double parseTimeValue(String time) {
        time = time.replaceAll("[^0-9AB]", "");
        if (time.isEmpty()) return 0.0;

        if (time.contains("A")) {
            return Double.parseDouble(time.replace("A", ""));
        } else if (time.contains("B")) {
            return Double.parseDouble(time.replace("B", "")) + 0.5;
        } else {
            return Double.parseDouble(time);
        }
    }

    private List<inu.timetable.entity.Schedule> mergeSchedules(List<inu.timetable.entity.Schedule> schedules) {
        if (schedules.size() < 2) return schedules;

        // 요일, 시작시간 순 정렬
        schedules.sort((a, b) -> {
            int dayCompare = "월화수목금토일".indexOf(a.getDayOfWeek()) - "월화수목금토일".indexOf(b.getDayOfWeek());
            if (dayCompare != 0) return dayCompare;
            return Double.compare(a.getStartTime(), b.getStartTime());
        });

        List<inu.timetable.entity.Schedule> merged = new ArrayList<>();
        inu.timetable.entity.Schedule current = schedules.get(0);

        for (int i = 1; i < schedules.size(); i++) {
            inu.timetable.entity.Schedule next = schedules.get(i);

            if (current.getDayOfWeek().equals(next.getDayOfWeek()) &&
                Math.abs(current.getEndTime() - next.getStartTime()) < 0.001) {
                current.setEndTime(next.getEndTime());
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);

        return merged;
    }

    private inu.timetable.enums.SubjectType parseSubjectTypeEnum(String type) {
        if (type == null || type.isBlank()) return inu.timetable.enums.SubjectType.일선;

        return switch (type.trim()) {
            case "전공심화", "전심" -> inu.timetable.enums.SubjectType.전심;
            case "전공핵심", "전핵" -> inu.timetable.enums.SubjectType.전핵;
            case "심화교양", "심교" -> inu.timetable.enums.SubjectType.심교;
            case "핵심교양", "핵교" -> inu.timetable.enums.SubjectType.핵교;
            case "기초교양", "기교" -> inu.timetable.enums.SubjectType.기교;
            case "전공기초", "전기" -> inu.timetable.enums.SubjectType.전기;
            case "군사학" -> inu.timetable.enums.SubjectType.군사학;
            case "교직" -> inu.timetable.enums.SubjectType.교직;
            default -> inu.timetable.enums.SubjectType.일선;
        };
    }

    private inu.timetable.enums.ClassMethod parseClassMethodEnum(String method) {
        if (method == null || method.isBlank() || "-".equals(method.trim())) {
            return inu.timetable.enums.ClassMethod.OFFLINE;
        }

        String normalized = method.toUpperCase();
        if (normalized.contains("ONLINE") || normalized.contains("온라인") ||
            normalized.contains("E-LEARNING") || normalized.contains("비대면")) {
            return inu.timetable.enums.ClassMethod.ONLINE;
        }
        if (normalized.contains("BLENDED") || normalized.contains("블렌디드") || normalized.contains("혼합")) {
            return inu.timetable.enums.ClassMethod.BLENDED;
        }
        return inu.timetable.enums.ClassMethod.OFFLINE;
    }
}
