package inu.timetable.service;

import inu.timetable.dto.SubjectManagementRequest;
import inu.timetable.dto.SubjectManagementResponse;
import inu.timetable.entity.Schedule;
import inu.timetable.entity.Subject;
import inu.timetable.repository.SubjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class SubjectAdminService {

    private final SubjectRepository subjectRepository;

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
        return SubjectManagementResponse.from(savedSubject);
    }

    @Transactional
    public SubjectManagementResponse updateSubject(Long id, SubjectManagementRequest request) {
        Subject subject = findSubject(id);
        applyRequest(subject, request);
        return SubjectManagementResponse.from(subject);
    }

    @Transactional
    public void deleteSubject(Long id) {
        Subject subject = findSubject(id);
        try {
            subjectRepository.delete(subject);
            subjectRepository.flush();
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
        subject.setSubjectName(request.getSubjectName().trim());
        subject.setCredits(request.getCredits());
        subject.setProfessor(request.getProfessor().trim());
        subject.setDepartment(trimToNull(request.getDepartment()));
        subject.setGrade(request.getGrade());
        subject.setSubjectType(request.getSubjectType());
        subject.setClassMethod(request.getClassMethod());
        subject.setIsNight(request.getIsNight());

        subject.getSchedules().clear();
        for (SubjectManagementRequest.ScheduleRequest scheduleRequest : request.getSchedules()) {
            validateSchedule(scheduleRequest);
            Schedule schedule = Schedule.builder()
                    .subject(subject)
                    .dayOfWeek(scheduleRequest.getDayOfWeek().trim())
                    .startTime(scheduleRequest.getStartTime())
                    .endTime(scheduleRequest.getEndTime())
                    .build();
            subject.getSchedules().add(schedule);
        }
    }

    private void validateSchedule(SubjectManagementRequest.ScheduleRequest scheduleRequest) {
        if (scheduleRequest.getEndTime() <= scheduleRequest.getStartTime()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Schedule endTime must be greater than startTime");
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
