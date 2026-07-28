package inu.timetable.service;

import inu.timetable.dto.SubjectManagementRequest;
import inu.timetable.dto.SubjectManagementResponse;
import inu.timetable.entity.Schedule;
import inu.timetable.entity.Subject;
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
}
