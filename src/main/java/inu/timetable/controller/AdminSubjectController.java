package inu.timetable.controller;

import inu.timetable.dto.OfficialSubjectImportResponse;
import inu.timetable.dto.SubjectManagementRequest;
import inu.timetable.dto.SubjectManagementResponse;
import inu.timetable.entity.Schedule;
import inu.timetable.entity.Subject;
import inu.timetable.enums.ClassMethod;
import inu.timetable.enums.SubjectType;
import inu.timetable.event.SubjectDataChangedEvent;
import inu.timetable.repository.SubjectRepository;
import inu.timetable.service.AdminAccessGuard;
import inu.timetable.service.AdminOperationLockService;
import inu.timetable.service.OfficialSubjectImportService;
import inu.timetable.service.SubjectAdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/admin/api/subjects")
@RequiredArgsConstructor
public class AdminSubjectController {

    private final SubjectRepository subjectRepository;
    private final AdminAccessGuard adminAccessGuard;
    private final AdminOperationLockService adminOperationLockService;
    private final SubjectAdminService subjectAdminService;
    private final OfficialSubjectImportService officialSubjectImportService;
    private final ApplicationEventPublisher eventPublisher;

    @PostMapping
    public ResponseEntity<SubjectManagementResponse> createSubject(
            HttpServletRequest servletRequest,
            @Valid @RequestBody SubjectManagementRequest request) {
        adminAccessGuard.requireAuthenticated(servletRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(subjectAdminService.createSubject(request));
    }

    @GetMapping("/{id}")
    public SubjectManagementResponse getSubject(
            HttpServletRequest servletRequest,
            @PathVariable Long id) {
        adminAccessGuard.requireAuthenticated(servletRequest);
        return subjectAdminService.getSubject(id);
    }

    @PutMapping("/{id}")
    public SubjectManagementResponse updateSubject(
            HttpServletRequest servletRequest,
            @PathVariable Long id,
            @Valid @RequestBody SubjectManagementRequest request) {
        adminAccessGuard.requireAuthenticated(servletRequest);
        return subjectAdminService.updateSubject(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteSubject(
            HttpServletRequest servletRequest,
            @PathVariable Long id) {
        adminAccessGuard.requireAuthenticated(servletRequest);
        subjectAdminService.deleteSubject(id);
        return ResponseEntity.ok(Map.of("deleted", true, "id", id));
    }

    @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public OfficialSubjectImportResponse previewOfficialExcelImport(
            HttpServletRequest servletRequest,
            @RequestParam("file") MultipartFile file,
            @RequestParam String semester) throws IOException {
        adminAccessGuard.requireAuthenticated(servletRequest);
        return officialSubjectImportService.preview(file, semester);
    }

    @PostMapping(value = "/import/apply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public OfficialSubjectImportResponse applyOfficialExcelImport(
            HttpServletRequest servletRequest,
            @RequestParam("file") MultipartFile file,
            @RequestParam String semester,
            @RequestParam(defaultValue = "true") boolean deactivateMissing) throws IOException {
        adminAccessGuard.requireAuthenticated(servletRequest);
        return adminOperationLockService.runExclusive("subject-import-apply",
                () -> officialSubjectImportService.apply(file, semester, deactivateMissing));
    }

    @PostMapping("/manual")
    public List<Subject> addSubjectsManually(
            HttpServletRequest servletRequest,
            @RequestBody List<Map<String, Object>> subjectsData) {
        adminAccessGuard.requireAuthenticated(servletRequest);
        List<Subject> subjects = new ArrayList<>();

        for (Map<String, Object> data : subjectsData) {
            Subject subject = Subject.builder()
                    .subjectName((String) data.get("subjectName"))
                    .credits((Integer) data.get("credits"))
                    .professor((String) data.get("professor"))
                    .isNight((Boolean) data.get("isNight"))
                    .subjectType(parseSubjectType((String) data.get("subjectType")))
                    .classMethod(parseClassMethod((String) data.get("classMethod")))
                    .grade((Integer) data.get("grade"))
                    .department((String) data.get("department"))
                    .schedules(new ArrayList<>())
                    .build();

            String timeString = (String) data.get("timeString");
            if (timeString != null && !timeString.isBlank()) {
                List<Schedule> schedules = parseTime(timeString);
                for (Schedule schedule : schedules) {
                    schedule.setSubject(subject);
                    subject.getSchedules().add(schedule);
                }
            }

            subjects.add(subject);
        }

        List<Subject> savedSubjects = subjectRepository.saveAll(subjects);
        if (!savedSubjects.isEmpty()) {
            eventPublisher.publishEvent(new SubjectDataChangedEvent("manual-subject-import"));
        }
        return savedSubjects;
    }

    private List<Schedule> parseTime(String timeString) {
        List<Schedule> schedules = new ArrayList<>();
        if (timeString == null || timeString.isBlank()) {
            return schedules;
        }

        String cleanTimeString = timeString.replaceAll("\\([^)]*\\)", "").trim();

        Pattern dayPattern = Pattern.compile("([월화수목금토일])\\s+([^월화수목금토일]+)");
        Matcher dayMatcher = dayPattern.matcher(cleanTimeString);

        while (dayMatcher.find()) {
            String dayOfWeek = dayMatcher.group(1);
            String timeSlots = dayMatcher.group(2).trim();

            double minStartTime = Double.MAX_VALUE;
            double maxEndTime = Double.MIN_VALUE;

            Pattern rangePattern = Pattern.compile("((?:야)?[1-9][0-9]?[AB]?)-((?:야)?[1-9][0-9]?[AB]?)");
            Matcher rangeMatcher = rangePattern.matcher(timeSlots);

            boolean hasRange = false;
            while (rangeMatcher.find()) {
                String startPeriod = rangeMatcher.group(1);
                String endPeriod = rangeMatcher.group(2);

                double start = convertToTime(startPeriod);
                double end = convertToTimeEnd(endPeriod, startPeriod);
                minStartTime = Math.min(minStartTime, start);
                maxEndTime = Math.max(maxEndTime, end);
                hasRange = true;
            }

            if (!hasRange) {
                List<Double> times = new ArrayList<>();
                Pattern timePattern = Pattern.compile("(야[1-3]|[1-9][0-9]?[AB]?)");
                Matcher timeMatcher = timePattern.matcher(timeSlots);

                while (timeMatcher.find()) {
                    times.add(convertToTime(timeMatcher.group(1)));
                }

                if (!times.isEmpty()) {
                    minStartTime = times.get(0);
                    maxEndTime = times.get(times.size() - 1) + 1.0;
                }
            }

            if (minStartTime != Double.MAX_VALUE) {
                // 야간 과목에서 startTime > endTime인 경우 endTime에 8을 더함
                if (minStartTime > maxEndTime) {
                    maxEndTime += 8.0;
                }
                schedules.add(
                        Schedule.builder().dayOfWeek(dayOfWeek).startTime(minStartTime).endTime(maxEndTime).build());
            }
        }
        return schedules;
    }

    private double convertToTime(String period) {
        if (period.startsWith("야")) {
            String numericPart = period.substring(1);
            if (numericPart.equals("1"))
                return 10.0;
            if (numericPart.equals("2"))
                return 11.0;
            if (numericPart.equals("3"))
                return 12.0;
            return 10.0;
        }

        String numericPart = period.replaceAll("[^0-9]", "");
        if (numericPart.isEmpty())
            return 0.0;
        double time = Double.parseDouble(numericPart);

        if (period.contains("A")) {
            return time;
        } else if (period.contains("B")) {
            return time + 0.5;
        } else {
            return time;
        }
    }

    private double convertToTimeEnd(String period, String startPeriod) {
        if (period.startsWith("야")) {
            String numericPart = period.substring(1);
            if (numericPart.equals("1"))
                return 11.0;
            if (numericPart.equals("2"))
                return 12.0;
            if (numericPart.equals("3"))
                return 13.0;
            return 11.0;
        }

        if (startPeriod.startsWith("야")) {
            String numericPart = period.replaceAll("[^0-9]", "");
            if (numericPart.isEmpty())
                return 11.0;
            int nightTime = Integer.parseInt(numericPart);

            if (period.contains("A")) {
                return (9 + nightTime) + 0.5;
            } else if (period.contains("B")) {
                return (9 + nightTime) + 1.0;
            } else {
                return (9 + nightTime) + 1.0;
            }
        }

        String numericPart = period.replaceAll("[^0-9]", "");
        if (numericPart.isEmpty())
            return 0.0;
        double time = Double.parseDouble(numericPart);

        if (period.contains("A")) {
            return time + 0.5;
        } else if (period.contains("B")) {
            return time + 1.0;
        } else {
            return time + 1.0;
        }
    }

    private ClassMethod parseClassMethod(String method) {
        if (method == null)
            return ClassMethod.OFFLINE;
        return switch (method.toUpperCase()) {
            case "ONLINE" -> ClassMethod.ONLINE;
            case "BLENDED" -> ClassMethod.BLENDED;
            default -> ClassMethod.OFFLINE;
        };
    }

    private SubjectType parseSubjectType(String type) {
        if (type == null)
            return SubjectType.일선;
        return switch (type) {
            case "전심" -> SubjectType.전심;
            case "전핵" -> SubjectType.전핵;
            case "심교" -> SubjectType.심교;
            case "핵교" -> SubjectType.핵교;
            case "기교" -> SubjectType.기교;
            case "전기" -> SubjectType.전기;
            case "군사학" -> SubjectType.군사학;
            case "교직" -> SubjectType.교직;
            default -> SubjectType.일선;
        };
    }
}
