package inu.timetable.controller;

import inu.timetable.entity.Subject;
import inu.timetable.entity.Schedule;
import inu.timetable.enums.SubjectType;
import inu.timetable.enums.ClassMethod;
import inu.timetable.repository.SubjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController 
@RequestMapping("/api/subjects")
@RequiredArgsConstructor
public class SubjectController {

    private final SubjectRepository subjectRepository;

    @GetMapping
    public Page<Subject> getAllSubjects(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return subjectRepository.findAll(pageable);
    }

    @GetMapping("/count")
    public long getCount() {
        return subjectRepository.count();
    }

    @GetMapping("/type/{type}")
    public List<Subject> getSubjectsByType(@PathVariable SubjectType type) {
        return subjectRepository.findBySubjectType(type);
    }

    @GetMapping("/department/{department}")
    public List<Subject> getSubjectsByDepartment(@PathVariable String department) {
        return subjectRepository.findByDepartment(department);
    }

    @GetMapping("/grade/{grade}")
    public List<Subject> getSubjectsByGrade(@PathVariable Integer grade) {
        return subjectRepository.findByGrade(grade);
    }

    @GetMapping("/professor/{professor}")
    public List<Subject> getSubjectsByProfessor(@PathVariable String professor) {
        return subjectRepository.findByProfessor(professor);
    }

    @GetMapping("/search")
    public List<Subject> searchSubjects(@RequestParam String keyword) {
        return subjectRepository.findBySubjectNameContaining(keyword);
    }

    @GetMapping("/filter")
    public List<Subject> filterSubjects(
            @RequestParam(required = false) String subjectName,
            @RequestParam(required = false) String professor,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String dayOfWeek,
            @RequestParam(required = false) Double startTime,
            @RequestParam(required = false) Double endTime,
            @RequestParam(required = false) SubjectType subjectType,
            @RequestParam(required = false) Integer grade,
            @RequestParam(required = false) Boolean isNight) {
        
        // 문자열 파라미터를 LIKE 패턴으로 변환
        String subjectNamePattern = subjectName != null ? "%" + subjectName + "%" : null;
        String professorPattern = professor != null ? "%" + professor + "%" : null;
        String departmentPattern = department != null ? "%" + department + "%" : null;
        
        return subjectRepository.findWithFilters(
            subjectNamePattern, professorPattern, departmentPattern, dayOfWeek, 
            startTime, endTime, subjectType, grade, isNight
        );
    }

    @PostMapping("/manual")
    public List<Subject> addSubjectsManually(@RequestBody List<Map<String, Object>> subjectsData) {
        List<Subject> subjects = new ArrayList<>();
        
        for (Map<String, Object> data : subjectsData) {
            String timeString = (String) data.get("timeString");
            List<Schedule> schedules = parseTime(timeString);
            
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
            
            for (Schedule schedule : schedules) {
                schedule.setSubject(subject);
                subject.getSchedules().add(schedule);
            }
            
            subjects.add(subject);
        }
        
        return subjectRepository.saveAll(subjects);
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
                schedules.add(
                    Schedule.builder().dayOfWeek(dayOfWeek).startTime(minStartTime).endTime(maxEndTime).build());
            }
        }
        return schedules;
    }

    private double convertToTime(String period) {
        if (period.startsWith("야")) {
            String numericPart = period.substring(1);
            if (numericPart.equals("1")) return 10.0;
            if (numericPart.equals("2")) return 11.0;
            if (numericPart.equals("3")) return 12.0;
            return 10.0;
        }
        
        String numericPart = period.replaceAll("[^0-9]", "");
        if (numericPart.isEmpty()) return 0.0;
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
            if (numericPart.equals("1")) return 11.0;
            if (numericPart.equals("2")) return 12.0;
            if (numericPart.equals("3")) return 13.0;
            return 11.0;
        }
        
        if (startPeriod.startsWith("야")) {
            String numericPart = period.replaceAll("[^0-9]", "");
            if (numericPart.isEmpty()) return 11.0;
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
        if (numericPart.isEmpty()) return 0.0;
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
        if (method == null) return ClassMethod.OFFLINE;
        return switch (method.toUpperCase()) {
            case "ONLINE" -> ClassMethod.ONLINE;
            case "BLENDED" -> ClassMethod.BLENDED;
            default -> ClassMethod.OFFLINE;
        };
    }

    private SubjectType parseSubjectType(String type) {
        if (type == null) return SubjectType.일선;
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