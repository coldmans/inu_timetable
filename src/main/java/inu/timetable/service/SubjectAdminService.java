package inu.timetable.service;

import inu.timetable.dto.SubjectManagementRequest;
import inu.timetable.dto.SubjectManagementResponse;
import inu.timetable.entity.Schedule;
import inu.timetable.entity.Subject;
import inu.timetable.enums.ClassMethod;
import inu.timetable.enums.SubjectType;
import inu.timetable.event.SubjectDataChangedEvent;
import inu.timetable.repository.SubjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubjectAdminService {

    private static final String DAYS = "월화수목금토일";

    private final SubjectRepository subjectRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public SubjectManagementResponse getSubject(Long id) {
        return SubjectManagementResponse.from(findSubject(id));
    }

    // 관리자 화면의 학기 필터 옵션. 비활성 과목의 학기도 포함해 최신순으로 반환한다.
    @Transactional(readOnly = true)
    public List<String> getSemesters() {
        return subjectRepository.findDistinctSemesters();
    }

    @Transactional
    public List<SubjectManagementResponse> addSubjectsManually(List<Map<String, Object>> subjectsData) {
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
            publishSubjectDataChanged("manual-subject-import");
        }
        return savedSubjects.stream()
                .map(SubjectManagementResponse::from)
                .toList();
    }

    @Transactional
    public SubjectManagementResponse createSubject(SubjectManagementRequest request) {
        Subject subject = Subject.builder()
                .schedules(new ArrayList<>())
                .build();
        applyRequest(subject, request);

        Subject savedSubject = subjectRepository.save(subject);
        publishSubjectDataChanged("admin-create");
        return SubjectManagementResponse.from(savedSubject);
    }

    @Transactional
    public SubjectManagementResponse updateSubject(Long id, SubjectManagementRequest request) {
        Subject subject = findSubject(id);
        applyRequest(subject, request);
        publishSubjectDataChanged("admin-update");
        return SubjectManagementResponse.from(subject);
    }

    @Transactional
    public void deleteSubject(Long id) {
        Subject subject = findSubject(id);
        try {
            subjectRepository.delete(subject);
            subjectRepository.flush();
            publishSubjectDataChanged("admin-delete");
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Subject is used by user timetable or wishlist data",
                    exception);
        }
    }

    private Subject findSubject(Long id) {
        return subjectRepository.findWithSchedulesById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subject not found"));
    }

    private void applyRequest(Subject subject, SubjectManagementRequest request) {
        subject.setCourseCode(trimToNull(request.getCourseCode()));
        subject.setSemester(trimToNull(request.getSemester()));
        subject.setActive(request.getActive() == null || request.getActive());
        subject.setSubjectName(request.getSubjectName().trim());
        subject.setCredits(request.getCredits());
        subject.setProfessor(request.getProfessor().trim());
        subject.setDepartment(trimToNull(request.getDepartment()));
        subject.setGrade(request.getGrade());
        subject.setSubjectType(request.getSubjectType());
        subject.setClassMethod(request.getClassMethod());
        subject.setIsNight(request.getIsNight());

        synchronizeSchedules(subject, request.getSchedules());
    }

    private void synchronizeSchedules(
            Subject subject,
            List<SubjectManagementRequest.ScheduleRequest> scheduleRequests) {
        scheduleRequests.forEach(this::validateSchedule);

        Map<String, Schedule> existingByKey = subject.getSchedules().stream()
                .collect(Collectors.toMap(
                        schedule -> scheduleKey(
                                schedule.getDayOfWeek(),
                                schedule.getStartTime(),
                                schedule.getEndTime()),
                        schedule -> schedule,
                        (left, right) -> left,
                        LinkedHashMap::new));
        Set<String> incomingKeys = scheduleRequests.stream()
                .map(request -> scheduleKey(
                        request.getDayOfWeek().trim(),
                        request.getStartTime(),
                        request.getEndTime()))
                .collect(Collectors.toSet());

        subject.getSchedules().removeIf(schedule -> !incomingKeys.contains(scheduleKey(
                schedule.getDayOfWeek(),
                schedule.getStartTime(),
                schedule.getEndTime())));

        for (SubjectManagementRequest.ScheduleRequest scheduleRequest : scheduleRequests) {
            String dayOfWeek = scheduleRequest.getDayOfWeek().trim();
            String key = scheduleKey(
                    dayOfWeek,
                    scheduleRequest.getStartTime(),
                    scheduleRequest.getEndTime());
            Schedule schedule = existingByKey.get(key);
            if (schedule == null) {
                schedule = Schedule.builder()
                        .subject(subject)
                        .dayOfWeek(dayOfWeek)
                        .startTime(scheduleRequest.getStartTime())
                        .endTime(scheduleRequest.getEndTime())
                        .build();
                subject.getSchedules().add(schedule);
                existingByKey.put(key, schedule);
            } else {
                schedule.setSubject(subject);
            }
        }

        subject.getSchedules().sort((left, right) -> {
            int dayCompare = Integer.compare(
                    DAYS.indexOf(left.getDayOfWeek()),
                    DAYS.indexOf(right.getDayOfWeek()));
            return dayCompare != 0 ? dayCompare : Double.compare(left.getStartTime(), right.getStartTime());
        });
    }

    private void validateSchedule(SubjectManagementRequest.ScheduleRequest scheduleRequest) {
        if (scheduleRequest.getEndTime() <= scheduleRequest.getStartTime()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Schedule endTime must be greater than startTime");
        }
    }

    private String scheduleKey(String dayOfWeek, Double startTime, Double endTime) {
        return dayOfWeek + ":" + startTime + "-" + endTime;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void publishSubjectDataChanged(String source) {
        eventPublisher.publishEvent(new SubjectDataChangedEvent(source));
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
