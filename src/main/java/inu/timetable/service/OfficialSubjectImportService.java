package inu.timetable.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import inu.timetable.dto.OfficialSubjectImportResponse;
import inu.timetable.entity.Schedule;
import inu.timetable.entity.ScheduleRoomSegment;
import inu.timetable.entity.Subject;
import inu.timetable.enums.ClassMethod;
import inu.timetable.enums.SubjectType;
import inu.timetable.event.SubjectDataChangedEvent;
import inu.timetable.repository.SubjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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
import java.nio.charset.StandardCharsets;
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

    private static final Pattern BRACKET_SCHEDULE_PATTERN = Pattern.compile("\\[([^:\\]]+):([^\\]]+)\\]");
    private static final Pattern DAY_PATTERN = Pattern.compile("^([월화수목금토일])\\s*(.*)$");
    private static final Pattern PERIOD_PATTERN = Pattern.compile("\\(([^)]+)\\)");
    private static final Pattern BARE_PERIOD_PATTERN = Pattern.compile("(?:야)?\\d{1,2}[AB]?(?:\\s*-\\s*(?:야)?\\d{1,2}[AB]?)?");
    private static final String DAYS = "월화수목금토일";
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private final SubjectRepository subjectRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final SubjectUpdateLogService subjectUpdateLogService;
    private final TimetableConflictResolutionService timetableConflictResolutionService;

    @Transactional(readOnly = true)
    public OfficialSubjectImportResponse preview(MultipartFile file, String semester) throws IOException {
        String normalizedSemester = normalizeSemester(semester);
        ParsedWorkbook parsed = parseOfficialFile(file, normalizedSemester);
        ImportDiff diff = diff(parsed.records(), loadImportCandidates(normalizedSemester));
        return toResponse(false, normalizedSemester, parsed.sourceFormat(), parsed.records(), diff, true);
    }

    @Transactional
    public OfficialSubjectImportResponse apply(MultipartFile file, String semester, boolean deactivateMissing)
            throws IOException {
        String normalizedSemester = normalizeSemester(semester);
        ParsedWorkbook parsed = parseOfficialFile(file, normalizedSemester);
        List<OfficialSubjectRecord> records = parsed.records();
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

        // 아래 충돌 정리에서 bulk delete 후 영속성 컨텍스트를 비우므로, 과목/스케줄 변경과
        // 미개설 active=false를 먼저 DB에 확정해 변경 내용이 함께 유실되지 않도록 한다.
        subjectRepository.flush();

        // 변경 영향을 받은 유저에게 알리고, 새 시간과 충돌하는 항목 및 미개설 과목을
        // 같은 트랜잭션에서 시간표로부터 제거한다.
        timetableConflictResolutionService.reconcileImportChanges(
                modifiedSubjects(diff, subjectsToSave),
                timeChangedSubjects(diff, subjectsToSave),
                deactivateMissing ? diff.removed() : List.of(),
                normalizedSemester);

        subjectUpdateLogService.record(
                normalizedSemester,
                parsed.sourceFormat(),
                diff.addedSubjects().size(),
                diff.modifiedSubjects().size(),
                deactivateMissing ? diff.removed().size() : 0);

        eventPublisher.publishEvent(new SubjectDataChangedEvent("official-subject-import"));
        return toResponse(true, normalizedSemester, parsed.sourceFormat(), records, diff, deactivateMissing);
    }

    // diff 에서 "시간표" 필드가 변경된 기존 과목만 추린다(스케줄은 applyRecord 로 이미 새 시간으로 갱신됨).
    private List<Subject> timeChangedSubjects(ImportDiff diff, List<Subject> subjectsToSave) {
        Set<Long> timeChangedSubjectIds = diff.modifiedSubjects().stream()
                .filter(item -> item.getChangedFields().contains("시간표"))
                .map(OfficialSubjectImportResponse.ModifiedSubjectImportItem::getId)
                .collect(Collectors.toSet());
        return subjectsWithIds(subjectsToSave, timeChangedSubjectIds);
    }

    private List<Subject> modifiedSubjects(ImportDiff diff, List<Subject> subjectsToSave) {
        Set<Long> modifiedSubjectIds = diff.modifiedSubjects().stream()
                .map(OfficialSubjectImportResponse.ModifiedSubjectImportItem::getId)
                .collect(Collectors.toSet());
        return subjectsWithIds(subjectsToSave, modifiedSubjectIds);
    }

    private List<Subject> subjectsWithIds(List<Subject> subjects, Set<Long> subjectIds) {
        return subjects.stream()
                .filter(subject -> subject.getId() != null && subjectIds.contains(subject.getId()))
                .toList();
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

        // 학수번호(courseCode)가 있는 과목만 '공식 엑셀에서 빠짐 → 비활성화' 대상으로 본다.
        // 학수번호 없는 레거시(PDF/AI 파싱) 과목은 공식 엑셀과 키가 겹칠 수 없어,
        // 이 필터가 없으면 공식 엑셀을 적용할 때마다 전량 비활성화되는 문제가 있었다.
        List<Subject> removed = candidates.stream()
                .filter(subject -> Boolean.TRUE.equals(subject.getActive()))
                .filter(subject -> hasText(subject.getCourseCode()))
                .filter(subject -> !incomingCodes.contains(subject.getCourseCode()))
                .toList();

        return new ImportDiff(existingByCode, addedSubjects, modifiedSubjects, removed, unchangedCount);
    }

    private OfficialSubjectImportResponse toResponse(
            boolean applied,
            String semester,
            String sourceFormat,
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
                .sourceFormat(sourceFormat)
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

    ParsedWorkbook parseOfficialFile(MultipartFile file, String semester) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "JSON 또는 Excel 파일이 필요합니다.");
        }
        String filename = defaultText(file.getOriginalFilename(), "").toLowerCase();
        String contentType = defaultText(file.getContentType(), "").toLowerCase();
        if (filename.endsWith(".json") || contentType.contains("json")) {
            return parseOfficialJson(file, semester);
        }
        return parseOfficialExcel(file, semester);
    }

    private ParsedWorkbook parseOfficialExcel(MultipartFile file, String semester) throws IOException {
        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            HeaderInfo headerInfo = findHeader(sheet, formatter);
            // 공식 종합강의시간표는 "시간표(교시)", 강의계획서 조회는 "시간표" 헤더를 쓴다.
            Integer timetableColumn = headerInfo.column("시간표(교시)", "시간표");
            String sourceFormat = headerInfo.optionalColumn("시간표(교시)") != null
                    ? "OFFICIAL_TIMETABLE"
                    : "SYLLABUS";
            validateFileSemester(sheet, headerInfo, semester, formatter);
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
                        hasNightSchedule(cellValue(row, timetableColumn, formatter)),
                        parseSyllabusAvailability(cellValue(
                                row,
                                headerInfo.optionalColumn("강의계획서입력여부", "강의 계획서 입력여부"),
                                formatter)),
                        parseSchedules(cellValue(row, timetableColumn, formatter))));
            }

            if (!duplicateCourseCodes.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "중복 학수번호가 있습니다: " + String.join(", ", duplicateCourseCodes));
            }
            return new ParsedWorkbook(records, sourceFormat);
        }
    }

    private ParsedWorkbook parseOfficialJson(MultipartFile file, String semester) throws IOException {
        JsonNode root = JSON_MAPPER.readTree(file.getBytes());
        JsonNode rowsNode = root.path("rows");
        if (!rowsNode.isArray()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "JSON의 rows 배열을 찾을 수 없습니다.");
        }

        String[] semesterParts = semester.split("-", 2);
        String expectedYear = semesterParts[0];
        String expectedTerm = semesterParts.length == 2 ? semesterParts[1] : "";
        List<OfficialSubjectRecord> records = new ArrayList<>();
        Set<String> courseCodes = new LinkedHashSet<>();
        Set<String> duplicateCourseCodes = new LinkedHashSet<>();

        for (JsonNode row : rowsNode) {
            String year = text(row, "yy");
            String term = normalizeJsonTerm(text(row, "tmGbn"));
            if (!expectedYear.equals(year) || !expectedTerm.equals(term)) {
                continue;
            }

            String courseCode = text(row, "haksuNo");
            String subjectName = text(row, "scNm");
            if (!hasText(courseCode) && !hasText(subjectName)) {
                continue;
            }
            if (!hasText(courseCode)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "학수번호가 비어 있는 JSON 행이 있습니다.");
            }
            if (!courseCodes.add(courseCode)) {
                duplicateCourseCodes.add(courseCode);
            }

            String timeSchedule = text(row, "timeNm");
            records.add(new OfficialSubjectRecord(
                    courseCode,
                    semester,
                    subjectName,
                    parseJsonCredits(row.path("hp")),
                    defaultText(text(row, "profNm"), "미배정"),
                    trimToNull(text(row, "hgMjNm")),
                    parseGrade(text(row, "hySeqGbn")),
                    parseSubjectType(text(row, "cptnGbn")),
                    parseClassMethod(firstText(row, "lsnTypeGbn", "lsnGbn", "lsnMthdGbn")),
                    hasNightSchedule(timeSchedule),
                    "1".equals(text(row, "inptGbn")),
                    parseSchedules(timeSchedule)));
        }

        if (records.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "선택한 학기(" + semester + ")에 해당하는 JSON 과목이 없습니다.");
        }
        if (!duplicateCourseCodes.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "중복 학수번호가 있습니다: " + String.join(", ", duplicateCourseCodes));
        }
        return new ParsedWorkbook(records, "SYLLABUS_JSON");
    }

    private String normalizeJsonTerm(String term) {
        return switch (term) {
            case "10" -> "1";
            case "20" -> "2";
            case "30" -> "여름";
            case "40" -> "겨울";
            default -> term;
        };
    }

    private int parseJsonCredits(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return 0;
        }
        if (node.isNumber()) {
            return (int) Math.round(node.asDouble());
        }
        if (node.isObject() && node.has("hi")) {
            return node.path("hi").asInt();
        }
        return parseCredits(node.asText());
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("").trim();
    }

    record ParsedWorkbook(List<OfficialSubjectRecord> records, String sourceFormat) {
    }

    // 강의계획서 조회 파일에는 년도/학기 컬럼이 있으므로, 업로드 시 선택한 학기와
    // 파일 데이터의 학기가 다르면 잘못된 학기로 반영되는 사고를 막기 위해 거부한다.
    private void validateFileSemester(Sheet sheet, HeaderInfo headerInfo, String semester, DataFormatter formatter) {
        Integer yearColumn = headerInfo.optionalColumn("년도");
        Integer termColumn = headerInfo.optionalColumn("학기");
        if (yearColumn == null || termColumn == null) {
            return;
        }

        for (int rowIndex = headerInfo.rowIndex() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            String year = cellValue(row, yearColumn, formatter).replaceAll("[^0-9]", "");
            String term = cellValue(row, termColumn, formatter).replaceAll("[^0-9]", "");
            if (!hasText(year) || !hasText(term)) {
                continue;
            }
            String fileSemester = year + "-" + term;
            if (!fileSemester.equals(semester)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "파일의 학기(" + fileSemester + ")와 업로드 학기(" + semester + ")가 다릅니다.");
            }
            return;
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

    void applyRecord(Subject subject, OfficialSubjectRecord record) {
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
        subject.setSyllabusAvailable(record.syllabusAvailable());

        synchronizeSchedules(subject, record.schedules());
    }

    private void synchronizeSchedules(Subject subject, List<ScheduleValue> scheduleValues) {
        Map<String, Schedule> existingByKey = subject.getSchedules().stream()
                .collect(Collectors.toMap(
                        schedule -> scheduleKey(
                                schedule.getDayOfWeek(),
                                schedule.getStartTime(),
                                schedule.getEndTime()),
                        schedule -> schedule,
                        (left, right) -> left,
                        LinkedHashMap::new));
        Set<String> incomingKeys = scheduleValues.stream()
                .map(schedule -> scheduleKey(schedule.dayOfWeek(), schedule.startTime(), schedule.endTime()))
                .collect(Collectors.toSet());

        subject.getSchedules().removeIf(schedule -> !incomingKeys.contains(scheduleKey(
                schedule.getDayOfWeek(),
                schedule.getStartTime(),
                schedule.getEndTime())));

        for (ScheduleValue scheduleValue : scheduleValues) {
            String key = scheduleKey(
                    scheduleValue.dayOfWeek(),
                    scheduleValue.startTime(),
                    scheduleValue.endTime());
            Schedule schedule = existingByKey.get(key);
            if (schedule == null) {
                schedule = Schedule.builder()
                        .subject(subject)
                        .dayOfWeek(scheduleValue.dayOfWeek())
                        .startTime(scheduleValue.startTime())
                        .endTime(scheduleValue.endTime())
                        .build();
                subject.getSchedules().add(schedule);
                existingByKey.put(key, schedule);
            } else {
                schedule.setSubject(subject);
                schedule.setDayOfWeek(scheduleValue.dayOfWeek());
                schedule.setStartTime(scheduleValue.startTime());
                schedule.setEndTime(scheduleValue.endTime());
            }
            synchronizeRoomSegments(schedule, scheduleValue.roomSegments());
        }

        subject.getSchedules().sort((left, right) -> {
            int dayCompare = Integer.compare(
                    DAYS.indexOf(left.getDayOfWeek()),
                    DAYS.indexOf(right.getDayOfWeek()));
            return dayCompare != 0 ? dayCompare : Double.compare(left.getStartTime(), right.getStartTime());
        });
    }

    private void synchronizeRoomSegments(Schedule schedule, List<RoomSegmentValue> roomSegmentValues) {
        Map<String, ScheduleRoomSegment> existingByKey = schedule.getRoomSegments().stream()
                .collect(Collectors.toMap(
                        segment -> roomSegmentKey(
                                segment.getRoom(),
                                segment.getStartTime(),
                                segment.getEndTime()),
                        segment -> segment,
                        (left, right) -> left,
                        LinkedHashMap::new));
        Set<String> incomingKeys = roomSegmentValues.stream()
                .map(segment -> roomSegmentKey(segment.room(), segment.startTime(), segment.endTime()))
                .collect(Collectors.toSet());

        schedule.getRoomSegments().removeIf(segment -> !incomingKeys.contains(roomSegmentKey(
                segment.getRoom(),
                segment.getStartTime(),
                segment.getEndTime())));

        for (RoomSegmentValue roomSegmentValue : roomSegmentValues) {
            String key = roomSegmentKey(
                    roomSegmentValue.room(),
                    roomSegmentValue.startTime(),
                    roomSegmentValue.endTime());
            ScheduleRoomSegment roomSegment = existingByKey.get(key);
            if (roomSegment == null) {
                roomSegment = ScheduleRoomSegment.builder()
                        .schedule(schedule)
                        .room(roomSegmentValue.room())
                        .startTime(roomSegmentValue.startTime())
                        .endTime(roomSegmentValue.endTime())
                        .build();
                schedule.getRoomSegments().add(roomSegment);
                existingByKey.put(key, roomSegment);
            } else {
                roomSegment.setSchedule(schedule);
            }
        }
    }

    List<String> changedFields(Subject subject, OfficialSubjectRecord record) {
        List<String> fields = new ArrayList<>();
        addIfChanged(fields, "교과목명", subject.getSubjectName(), record.subjectName());
        addIfChanged(fields, "학점", subject.getCredits(), record.credits());
        addIfChanged(fields, "담당교수", subject.getProfessor(), record.professor());
        addIfChanged(fields, "학과", subject.getDepartment(), record.department());
        addIfChanged(fields, "학년", subject.getGrade(), record.grade());
        addIfChanged(fields, "이수구분", subject.getSubjectType(), record.subjectType());
        addIfChanged(fields, "수업유형", subject.getClassMethod(), record.classMethod());
        addIfChanged(fields, "야간여부", subject.getIsNight(), record.isNight());
        addIfChanged(fields, "강의계획서 첨부", subject.getSyllabusAvailable(), record.syllabusAvailable());
        boolean schedulesChanged = !sameSchedules(subject.getSchedules(), record.schedules());
        if (schedulesChanged) {
            fields.add("시간표");
        } else if (!sameRoomSegments(subject.getSchedules(), record.schedules())) {
            fields.add("강의실");
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

    private boolean sameRoomSegments(List<Schedule> schedules, List<ScheduleValue> scheduleValues) {
        List<String> left = schedules.stream()
                .flatMap(schedule -> schedule.getRoomSegments().stream()
                        .map(segment -> scheduleRoomSegmentKey(
                                schedule.getDayOfWeek(),
                                segment.getRoom(),
                                segment.getStartTime(),
                                segment.getEndTime())))
                .sorted()
                .toList();
        List<String> right = scheduleValues.stream()
                .flatMap(schedule -> schedule.roomSegments().stream()
                        .map(segment -> scheduleRoomSegmentKey(
                                schedule.dayOfWeek(),
                                segment.room(),
                                segment.startTime(),
                                segment.endTime())))
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

    List<ScheduleValue> parseSchedules(String timeSchedule) {
        if (!hasText(timeSchedule) || "-".equals(timeSchedule.trim())) {
            return List.of();
        }

        List<RawScheduleValue> schedules = new ArrayList<>();
        Matcher bracketMatcher = BRACKET_SCHEDULE_PATTERN.matcher(timeSchedule);
        String currentDay = null;
        boolean foundBracket = false;
        while (bracketMatcher.find()) {
            foundBracket = true;
            currentDay = parseSegment(
                    bracketMatcher.group(2),
                    trimToNull(bracketMatcher.group(1)),
                    schedules,
                    currentDay);
        }
        if (!foundBracket) {
            parseSegment(timeSchedule, null, schedules, currentDay);
        }
        return mergeSchedules(schedules);
    }

    private String parseSegment(
            String segment,
            String room,
            List<RawScheduleValue> schedules,
            String currentDay) {
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
                addPeriod(schedules, currentDay, room, periodMatcher.group(1));
                foundPeriod = true;
            }

            if (!foundPeriod) {
                Matcher bareMatcher = BARE_PERIOD_PATTERN.matcher(part);
                while (bareMatcher.find()) {
                    addPeriod(schedules, currentDay, room, bareMatcher.group());
                }
            }
        }
        return currentDay;
    }

    private void addPeriod(
            List<RawScheduleValue> schedules,
            String dayOfWeek,
            String room,
            String period) {
        double[] range = parsePeriod(period);
        if (range != null) {
            schedules.add(new RawScheduleValue(dayOfWeek, range[0], range[1], room));
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

    private List<ScheduleValue> mergeSchedules(List<RawScheduleValue> schedules) {
        if (schedules.isEmpty()) {
            return List.of();
        }

        Map<String, RawScheduleValue> deduplicated = new LinkedHashMap<>();
        for (RawScheduleValue schedule : schedules) {
            deduplicated.put(scheduleKey(schedule.dayOfWeek(), schedule.startTime(), schedule.endTime()), schedule);
        }

        List<RawScheduleValue> sorted = new ArrayList<>(deduplicated.values());
        sorted.sort((left, right) -> {
            int dayCompare = Integer.compare(DAYS.indexOf(left.dayOfWeek()), DAYS.indexOf(right.dayOfWeek()));
            if (dayCompare != 0) {
                return dayCompare;
            }
            return Double.compare(left.startTime(), right.startTime());
        });

        List<ScheduleValue> merged = new ArrayList<>();
        ScheduleValue current = new ScheduleValue(
                sorted.get(0).dayOfWeek(),
                sorted.get(0).startTime(),
                sorted.get(0).endTime(),
                List.of());
        for (int index = 1; index < sorted.size(); index++) {
            RawScheduleValue next = sorted.get(index);
            if (current.dayOfWeek().equals(next.dayOfWeek())
                    && Math.abs(current.endTime() - next.startTime()) < 0.001) {
                current = new ScheduleValue(
                        current.dayOfWeek(),
                        current.startTime(),
                        next.endTime(),
                        List.of());
            } else {
                merged.add(current);
                current = new ScheduleValue(
                        next.dayOfWeek(),
                        next.startTime(),
                        next.endTime(),
                        List.of());
            }
        }
        merged.add(current);

        List<RoomSegmentValue> roomSegments = mergeRoomSegments(schedules);
        return merged.stream()
                .map(schedule -> new ScheduleValue(
                        schedule.dayOfWeek(),
                        schedule.startTime(),
                        schedule.endTime(),
                        roomSegments.stream()
                                .filter(segment -> segment.dayOfWeek().equals(schedule.dayOfWeek()))
                                .filter(segment -> segment.startTime() >= schedule.startTime() - 0.001)
                                .filter(segment -> segment.endTime() <= schedule.endTime() + 0.001)
                                .toList()))
                .toList();
    }

    private List<RoomSegmentValue> mergeRoomSegments(List<RawScheduleValue> schedules) {
        Map<String, RoomSegmentValue> deduplicated = new LinkedHashMap<>();
        schedules.stream()
                .filter(schedule -> hasText(schedule.room()))
                .forEach(schedule -> {
                    RoomSegmentValue segment = new RoomSegmentValue(
                            schedule.room(),
                            schedule.dayOfWeek(),
                            schedule.startTime(),
                            schedule.endTime());
                    deduplicated.put(
                            scheduleRoomSegmentKey(
                                    segment.dayOfWeek(),
                                    segment.room(),
                                    segment.startTime(),
                                    segment.endTime()),
                            segment);
                });

        List<RoomSegmentValue> sorted = new ArrayList<>(deduplicated.values());
        sorted.sort((left, right) -> {
            int dayCompare = Integer.compare(
                    DAYS.indexOf(left.dayOfWeek()),
                    DAYS.indexOf(right.dayOfWeek()));
            if (dayCompare != 0) {
                return dayCompare;
            }
            int timeCompare = Double.compare(left.startTime(), right.startTime());
            if (timeCompare != 0) {
                return timeCompare;
            }
            return left.room().compareTo(right.room());
        });

        List<RoomSegmentValue> merged = new ArrayList<>();
        for (RoomSegmentValue next : sorted) {
            if (merged.isEmpty()) {
                merged.add(next);
                continue;
            }
            RoomSegmentValue current = merged.get(merged.size() - 1);
            if (current.dayOfWeek().equals(next.dayOfWeek())
                    && current.room().equals(next.room())
                    && Math.abs(current.endTime() - next.startTime()) < 0.001) {
                merged.set(merged.size() - 1, new RoomSegmentValue(
                        current.room(),
                        current.dayOfWeek(),
                        current.startTime(),
                        next.endTime()));
            } else {
                merged.add(next);
            }
        }
        return merged;
    }

    private String scheduleKey(String dayOfWeek, Double startTime, Double endTime) {
        return dayOfWeek + ":" + startTime + "-" + endTime;
    }

    private String roomSegmentKey(String room, Double startTime, Double endTime) {
        return room + ":" + startTime + "-" + endTime;
    }

    private String scheduleRoomSegmentKey(
            String dayOfWeek,
            String room,
            Double startTime,
            Double endTime) {
        return dayOfWeek + ":" + roomSegmentKey(room, startTime, endTime);
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
                || normalized.contains("MOOC")
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
        // 강의계획서 조회 파일은 "3.0"처럼 소수점으로 표기된다. 소수점을 버리면 30이 되므로 실수로 파싱한다.
        String numeric = credits.replaceAll("[^0-9.]", "");
        if (!hasText(numeric)) {
            return 0;
        }
        try {
            return (int) Math.round(Double.parseDouble(numeric));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private boolean hasNightSchedule(String timeSchedule) {
        return hasText(timeSchedule) && timeSchedule.contains("야");
    }

    private boolean parseSyllabusAvailability(String value) {
        return "1".equals(value)
                || "Y".equalsIgnoreCase(value)
                || "입력".equals(value)
                || "첨부".equals(value);
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
        // 이름 후보를 순서대로 조회한다(파일 형식별 헤더 표기 차이 흡수).
        Integer column(String... names) {
            for (String name : names) {
                Integer index = columns.get(name);
                if (index != null) {
                    return index;
                }
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "필수 컬럼이 없습니다: " + String.join("/", names));
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

    record OfficialSubjectRecord(
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
            Boolean syllabusAvailable,
            List<ScheduleValue> schedules) {
    }

    record ScheduleValue(
            String dayOfWeek,
            Double startTime,
            Double endTime,
            List<RoomSegmentValue> roomSegments) {
    }

    private record RawScheduleValue(
            String dayOfWeek,
            Double startTime,
            Double endTime,
            String room) {
    }

    record RoomSegmentValue(
            String room,
            String dayOfWeek,
            Double startTime,
            Double endTime) {
    }

    private record ImportDiff(
            Map<String, Subject> existingByCode,
            List<OfficialSubjectImportResponse.SubjectImportItem> addedSubjects,
            List<OfficialSubjectImportResponse.ModifiedSubjectImportItem> modifiedSubjects,
            List<Subject> removed,
            int unchangedCount) {
    }
}
