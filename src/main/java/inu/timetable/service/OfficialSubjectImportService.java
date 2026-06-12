package inu.timetable.service;

import inu.timetable.dto.OfficialSubjectImportResponse;
import inu.timetable.entity.Schedule;
import inu.timetable.entity.Subject;
import inu.timetable.enums.ClassMethod;
import inu.timetable.enums.SubjectType;
import inu.timetable.repository.SubjectRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OfficialSubjectImportService {

    private static final Pattern BRACKET_TIME_PATTERN = Pattern.compile("\\[[^:\\]]+:([^\\]]+)\\]");
    private static final Pattern DAY_PATTERN = Pattern.compile("^([월화수목금토일])\\s*(.*)$");
    private static final Pattern PERIOD_PATTERN = Pattern.compile("\\(([^)]+)\\)");
    private static final Pattern BARE_PERIOD_PATTERN = Pattern.compile("(?:야)?\\d{1,2}[AB]?(?:\\s*-\\s*(?:야)?\\d{1,2}[AB]?)?");
    private static final String DAYS = "월화수목금토일";

    private final SubjectRepository subjectRepository;

    @Transactional(readOnly = true)
    public OfficialSubjectImportResponse preview(MultipartFile file, String semester) throws IOException {
        String normalizedSemester = normalizeSemester(semester);
        List<OfficialSubjectRecord> records = parseOfficialExcel(file, normalizedSemester);
        ImportDiff diff = diff(records, loadImportCandidates(normalizedSemester));
        return toResponse(false, normalizedSemester, records, diff, true);
    }

    @Transactional
    public OfficialSubjectImportResponse apply(MultipartFile file, String semester, boolean deactivateMissing)
            throws IOException {
        String normalizedSemester = normalizeSemester(semester);
        List<OfficialSubjectRecord> records = parseOfficialExcel(file, normalizedSemester);
        List<Subject> candidates = loadImportCandidates(normalizedSemester);
        ImportDiff diff = diff(records, candidates);

        List<Subject> subjectsToSave = new ArrayList<>();
        for (OfficialSubjectRecord record : records) {
            Subject subject = diff.existingByCode().get(record.courseCode());
            if (subject == null) {
                subject = Subject.builder()
                        .schedules(new ArrayList<>())
                        .build();
            }
            applyRecord(subject, record);
            subjectsToSave.add(subject);
        }
        subjectRepository.saveAll(subjectsToSave);

        if (deactivateMissing) {
            for (Subject subject : diff.removed()) {
                subject.setActive(false);
            }
        }

        return toResponse(true, normalizedSemester, records, diff, deactivateMissing);
    }

    private ImportDiff diff(List<OfficialSubjectRecord> records, List<Subject> candidates) {
        Map<String, Subject> existingByCode = candidates.stream()
                .filter(subject -> hasText(subject.getCourseCode()))
                .collect(Collectors.toMap(Subject::getCourseCode, subject -> subject, (left, right) -> left));

        List<OfficialSubjectImportResponse.SubjectImportItem> addedSubjects = new ArrayList<>();
        List<OfficialSubjectImportResponse.ModifiedSubjectImportItem> modifiedSubjects = new ArrayList<>();
        int unchangedCount = 0;

        for (OfficialSubjectRecord record : records) {
            Subject existing = existingByCode.get(record.courseCode());
            if (existing == null) {
                addedSubjects.add(toItem(record));
                continue;
            }

            List<String> changedFields = changedFields(existing, record);
            if (changedFields.isEmpty()) {
                unchangedCount++;
            } else {
                modifiedSubjects.add(toModifiedItem(existing, record, changedFields));
            }
        }

        Set<String> incomingCodes = records.stream()
                .map(OfficialSubjectRecord::courseCode)
                .collect(Collectors.toSet());

        List<Subject> removed = candidates.stream()
                .filter(subject -> Boolean.TRUE.equals(subject.getActive()))
                .filter(subject -> !incomingCodes.contains(subject.getCourseCode()))
                .toList();

        return new ImportDiff(existingByCode, addedSubjects, modifiedSubjects, removed, unchangedCount);
    }

    private OfficialSubjectImportResponse toResponse(
            boolean applied,
            String semester,
            List<OfficialSubjectRecord> records,
            ImportDiff diff,
            boolean includeRemoved) {
        List<OfficialSubjectImportResponse.SubjectImportItem> removedItems = includeRemoved
                ? diff.removed().stream().map(this::toItem).toList()
                : List.of();

        List<String> warnings = new ArrayList<>();
        long noScheduleCount = records.stream()
                .filter(record -> record.schedules().isEmpty())
                .count();
        if (noScheduleCount > 0) {
            warnings.add("시간표가 비어 있거나 별도 운영인 과목 " + noScheduleCount + "개는 시간표 없이 저장됩니다.");
        }
        if (!removedItems.isEmpty()) {
            warnings.add("삭제 예정 과목은 물리 삭제하지 않고 active=false로 비활성화합니다.");
        }

        return OfficialSubjectImportResponse.builder()
                .applied(applied)
                .semester(semester)
                .totalRows(records.size())
                .addedCount(diff.addedSubjects().size())
                .modifiedCount(diff.modifiedSubjects().size())
                .removedCount(removedItems.size())
                .unchangedCount(diff.unchangedCount())
                .addedSubjects(diff.addedSubjects())
                .modifiedSubjects(diff.modifiedSubjects())
                .removedSubjects(removedItems)
                .warnings(warnings)
                .build();
    }

    private List<Subject> loadImportCandidates(String semester) {
        return subjectRepository.findImportCandidatesBySemester(semester);
    }

    private List<OfficialSubjectRecord> parseOfficialExcel(MultipartFile file, String semester) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Excel file is required");
        }

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            HeaderInfo headerInfo = findHeader(sheet, formatter);
            List<OfficialSubjectRecord> records = new ArrayList<>();
            Set<String> courseCodes = new LinkedHashSet<>();
            Set<String> duplicateCourseCodes = new LinkedHashSet<>();

            for (int rowIndex = headerInfo.rowIndex() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }

                String courseCode = cellValue(row, headerInfo.column("학수번호"), formatter);
                String subjectName = cellValue(row, headerInfo.column("교과목명"), formatter);

                if (!hasText(courseCode) && !hasText(subjectName)) {
                    continue;
                }
                if (!hasText(courseCode)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "학수번호가 비어 있는 행이 있습니다: row " + (rowIndex + 1));
                }
                if (!courseCodes.add(courseCode)) {
                    duplicateCourseCodes.add(courseCode);
                }

                records.add(new OfficialSubjectRecord(
                        courseCode,
                        semester,
                        subjectName,
                        parseCredits(cellValue(row, headerInfo.column("학점"), formatter)),
                        defaultText(cellValue(row, headerInfo.column("담당교수"), formatter), "미배정"),
                        trimToNull(cellValue(row, headerInfo.column("학과(부)"), formatter)),
                        parseGrade(cellValue(row, headerInfo.column("학년"), formatter)),
                        parseSubjectType(cellValue(row, headerInfo.column("이수구분"), formatter)),
                        parseClassMethod(cellValue(row, headerInfo.optionalColumn("수업유형", "수업구분", "수업방법"), formatter)),
                        hasNightSchedule(cellValue(row, headerInfo.column("시간표(교시)"), formatter)),
                        parseSchedules(cellValue(row, headerInfo.column("시간표(교시)"), formatter))));
            }

            if (!duplicateCourseCodes.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "중복 학수번호가 있습니다: " + String.join(", ", duplicateCourseCodes));
            }
            return records;
        }
    }

    private HeaderInfo findHeader(Sheet sheet, DataFormatter formatter) {
        for (int rowIndex = 0; rowIndex <= Math.min(sheet.getLastRowNum(), 10); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }

            Map<String, Integer> columns = new HashMap<>();
            for (Cell cell : row) {
                String value = formatter.formatCellValue(cell).trim();
                if (hasText(value)) {
                    columns.put(value, cell.getColumnIndex());
                }
            }

            if (columns.containsKey("학수번호") && columns.containsKey("교과목명")) {
                return new HeaderInfo(rowIndex, columns);
            }
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "학수번호/교과목명 헤더를 찾을 수 없습니다.");
    }

    private void applyRecord(Subject subject, OfficialSubjectRecord record) {
        subject.setCourseCode(record.courseCode());
        subject.setSemester(record.semester());
        subject.setActive(true);
        subject.setSubjectName(record.subjectName());
        subject.setCredits(record.credits());
        subject.setProfessor(record.professor());
        subject.setDepartment(record.department());
        subject.setGrade(record.grade());
        subject.setSubjectType(record.subjectType());
        subject.setClassMethod(record.classMethod());
        subject.setIsNight(record.isNight());

        subject.getSchedules().clear();
        for (ScheduleValue scheduleValue : record.schedules()) {
            subject.getSchedules().add(Schedule.builder()
                    .subject(subject)
                    .dayOfWeek(scheduleValue.dayOfWeek())
                    .startTime(scheduleValue.startTime())
                    .endTime(scheduleValue.endTime())
                    .build());
        }
    }

    private List<String> changedFields(Subject subject, OfficialSubjectRecord record) {
        List<String> fields = new ArrayList<>();
        addIfChanged(fields, "교과목명", subject.getSubjectName(), record.subjectName());
        addIfChanged(fields, "학점", subject.getCredits(), record.credits());
        addIfChanged(fields, "담당교수", subject.getProfessor(), record.professor());
        addIfChanged(fields, "학과", subject.getDepartment(), record.department());
        addIfChanged(fields, "학년", subject.getGrade(), record.grade());
        addIfChanged(fields, "이수구분", subject.getSubjectType(), record.subjectType());
        addIfChanged(fields, "수업유형", subject.getClassMethod(), record.classMethod());
        addIfChanged(fields, "야간여부", subject.getIsNight(), record.isNight());
        if (!sameSchedules(subject.getSchedules(), record.schedules())) {
            fields.add("시간표");
        }
        if (!Boolean.TRUE.equals(subject.getActive())) {
            fields.add("비활성화 상태");
        }
        return fields;
    }

    private void addIfChanged(List<String> fields, String fieldName, Object left, Object right) {
        if (!Objects.equals(left, right)) {
            fields.add(fieldName);
        }
    }

    private boolean sameSchedules(List<Schedule> schedules, List<ScheduleValue> scheduleValues) {
        List<String> left = schedules.stream()
                .map(schedule -> scheduleKey(schedule.getDayOfWeek(), schedule.getStartTime(), schedule.getEndTime()))
                .sorted()
                .toList();
        List<String> right = scheduleValues.stream()
                .map(schedule -> scheduleKey(schedule.dayOfWeek(), schedule.startTime(), schedule.endTime()))
                .sorted()
                .toList();
        return left.equals(right);
    }

    private OfficialSubjectImportResponse.SubjectImportItem toItem(OfficialSubjectRecord record) {
        return OfficialSubjectImportResponse.SubjectImportItem.builder()
                .courseCode(record.courseCode())
                .subjectName(record.subjectName())
                .professor(record.professor())
                .department(record.department())
                .grade(record.grade())
                .subjectType(record.subjectType().name())
                .credits(record.credits())
                .build();
    }

    private OfficialSubjectImportResponse.SubjectImportItem toItem(Subject subject) {
        return OfficialSubjectImportResponse.SubjectImportItem.builder()
                .id(subject.getId())
                .courseCode(subject.getCourseCode())
                .subjectName(subject.getSubjectName())
                .professor(subject.getProfessor())
                .department(subject.getDepartment())
                .grade(subject.getGrade())
                .subjectType(subject.getSubjectType().name())
                .credits(subject.getCredits())
                .build();
    }

    private OfficialSubjectImportResponse.ModifiedSubjectImportItem toModifiedItem(
            Subject subject,
            OfficialSubjectRecord record,
            List<String> changedFields) {
        return OfficialSubjectImportResponse.ModifiedSubjectImportItem.builder()
                .id(subject.getId())
                .courseCode(record.courseCode())
                .subjectName(record.subjectName())
                .professor(record.professor())
                .department(record.department())
                .grade(record.grade())
                .subjectType(record.subjectType().name())
                .credits(record.credits())
                .changedFields(changedFields)
                .build();
    }

    private List<ScheduleValue> parseSchedules(String timeSchedule) {
        if (!hasText(timeSchedule) || "-".equals(timeSchedule.trim())) {
            return List.of();
        }

        List<String> timeSegments = new ArrayList<>();
        Matcher bracketMatcher = BRACKET_TIME_PATTERN.matcher(timeSchedule);
        while (bracketMatcher.find()) {
            timeSegments.add(bracketMatcher.group(1));
        }
        if (timeSegments.isEmpty()) {
            timeSegments.add(timeSchedule);
        }

        List<ScheduleValue> schedules = new ArrayList<>();
        String currentDay = null;
        for (String segment : timeSegments) {
            currentDay = parseSegment(segment, schedules, currentDay);
        }
        return mergeSchedules(schedules);
    }

    private String parseSegment(String segment, List<ScheduleValue> schedules, String currentDay) {
        for (String rawPart : segment.split(",")) {
            String part = rawPart.trim();
            if (!hasText(part)) {
                continue;
            }

            Matcher dayMatcher = DAY_PATTERN.matcher(part);
            if (dayMatcher.matches()) {
                currentDay = dayMatcher.group(1);
                part = dayMatcher.group(2).trim();
            }
            if (currentDay == null) {
                continue;
            }

            boolean foundPeriod = false;
            Matcher periodMatcher = PERIOD_PATTERN.matcher(part);
            while (periodMatcher.find()) {
                addPeriod(schedules, currentDay, periodMatcher.group(1));
                foundPeriod = true;
            }

            if (!foundPeriod) {
                Matcher bareMatcher = BARE_PERIOD_PATTERN.matcher(part);
                while (bareMatcher.find()) {
                    addPeriod(schedules, currentDay, bareMatcher.group());
                }
            }
        }
        return currentDay;
    }

    private void addPeriod(List<ScheduleValue> schedules, String dayOfWeek, String period) {
        double[] range = parsePeriod(period);
        if (range != null) {
            schedules.add(new ScheduleValue(dayOfWeek, range[0], range[1]));
        }
    }

    private double[] parsePeriod(String period) {
        if (!hasText(period)) {
            return null;
        }

        String normalized = period.replaceAll("\\s+", "");
        boolean isNight = normalized.contains("야");
        normalized = normalized.replace("야", "");

        double start;
        double end;
        if (normalized.contains("-")) {
            String[] parts = normalized.split("-", 2);
            start = parseTimeValue(parts[0]);
            end = parseTimeValue(parts[1]) + (parts[1].contains("A") || parts[1].contains("B") ? 0.5 : 1.0);
        } else {
            start = parseTimeValue(normalized);
            end = start + (normalized.contains("A") || normalized.contains("B") ? 0.5 : 1.0);
        }

        if (isNight) {
            start += 9.0;
            end += 9.0;
        }
        return new double[]{start, end};
    }

    private double parseTimeValue(String time) {
        String normalized = time.replaceAll("[^0-9AB]", "");
        if (!hasText(normalized)) {
            return 0.0;
        }
        if (normalized.contains("A")) {
            return Double.parseDouble(normalized.replace("A", ""));
        }
        if (normalized.contains("B")) {
            return Double.parseDouble(normalized.replace("B", "")) + 0.5;
        }
        return Double.parseDouble(normalized);
    }

    private List<ScheduleValue> mergeSchedules(List<ScheduleValue> schedules) {
        if (schedules.isEmpty()) {
            return List.of();
        }

        Map<String, ScheduleValue> deduplicated = new LinkedHashMap<>();
        for (ScheduleValue schedule : schedules) {
            deduplicated.put(scheduleKey(schedule.dayOfWeek(), schedule.startTime(), schedule.endTime()), schedule);
        }

        List<ScheduleValue> sorted = new ArrayList<>(deduplicated.values());
        sorted.sort((left, right) -> {
            int dayCompare = Integer.compare(DAYS.indexOf(left.dayOfWeek()), DAYS.indexOf(right.dayOfWeek()));
            if (dayCompare != 0) {
                return dayCompare;
            }
            return Double.compare(left.startTime(), right.startTime());
        });

        List<ScheduleValue> merged = new ArrayList<>();
        ScheduleValue current = sorted.get(0);
        for (int index = 1; index < sorted.size(); index++) {
            ScheduleValue next = sorted.get(index);
            if (current.dayOfWeek().equals(next.dayOfWeek())
                    && Math.abs(current.endTime() - next.startTime()) < 0.001) {
                current = new ScheduleValue(current.dayOfWeek(), current.startTime(), next.endTime());
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return merged;
    }

    private String scheduleKey(String dayOfWeek, Double startTime, Double endTime) {
        return dayOfWeek + ":" + startTime + "-" + endTime;
    }

    private SubjectType parseSubjectType(String type) {
        if (!hasText(type)) {
            return SubjectType.일선;
        }
        return switch (type.trim()) {
            case "전공심화", "전심" -> SubjectType.전심;
            case "전공핵심", "전핵" -> SubjectType.전핵;
            case "심화교양", "심교" -> SubjectType.심교;
            case "핵심교양", "핵교" -> SubjectType.핵교;
            case "기초교양", "기교" -> SubjectType.기교;
            case "전공기초", "전기" -> SubjectType.전기;
            case "군사학" -> SubjectType.군사학;
            case "교직" -> SubjectType.교직;
            default -> SubjectType.일선;
        };
    }

    private ClassMethod parseClassMethod(String method) {
        if (!hasText(method) || "-".equals(method.trim())) {
            return ClassMethod.OFFLINE;
        }
        String normalized = method.toUpperCase();
        if (normalized.contains("혼합") || normalized.contains("BLENDED")) {
            return ClassMethod.BLENDED;
        }
        if (normalized.contains("ONLINE")
                || normalized.contains("E-LEARNING")
                || normalized.contains("온라인")
                || normalized.contains("비대면")) {
            return ClassMethod.ONLINE;
        }
        return ClassMethod.OFFLINE;
    }

    private Integer parseGrade(String grade) {
        if (!hasText(grade) || grade.contains("전학년")) {
            return null;
        }
        String numeric = grade.replaceAll("[^0-9]", "");
        if (!hasText(numeric)) {
            return null;
        }
        int parsed = Integer.parseInt(numeric);
        return parsed >= 1 && parsed <= 4 ? parsed : null;
    }

    private int parseCredits(String credits) {
        if (!hasText(credits)) {
            return 0;
        }
        String numeric = credits.replaceAll("[^0-9]", "");
        return hasText(numeric) ? Integer.parseInt(numeric) : 0;
    }

    private boolean hasNightSchedule(String timeSchedule) {
        return hasText(timeSchedule) && timeSchedule.contains("야");
    }

    private String cellValue(Row row, Integer columnIndex, DataFormatter formatter) {
        if (row == null || columnIndex == null || columnIndex < 0) {
            return "";
        }
        Cell cell = row.getCell(columnIndex);
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private String normalizeSemester(String semester) {
        if (!hasText(semester)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "semester is required");
        }
        return semester.trim();
    }

    private String defaultText(String value, String defaultValue) {
        return hasText(value) ? value.trim() : defaultValue;
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record HeaderInfo(int rowIndex, Map<String, Integer> columns) {
        Integer column(String name) {
            Integer index = columns.get(name);
            if (index == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "필수 컬럼이 없습니다: " + name);
            }
            return index;
        }

        Integer optionalColumn(String... names) {
            for (String name : names) {
                Integer index = columns.get(name);
                if (index != null) {
                    return index;
                }
            }
            return null;
        }
    }

    private record OfficialSubjectRecord(
            String courseCode,
            String semester,
            String subjectName,
            Integer credits,
            String professor,
            String department,
            Integer grade,
            SubjectType subjectType,
            ClassMethod classMethod,
            Boolean isNight,
            List<ScheduleValue> schedules) {
    }

    private record ScheduleValue(String dayOfWeek, Double startTime, Double endTime) {
    }

    private record ImportDiff(
            Map<String, Subject> existingByCode,
            List<OfficialSubjectImportResponse.SubjectImportItem> addedSubjects,
            List<OfficialSubjectImportResponse.ModifiedSubjectImportItem> modifiedSubjects,
            List<Subject> removed,
            int unchangedCount) {
    }
}
