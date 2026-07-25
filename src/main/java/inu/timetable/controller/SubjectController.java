package inu.timetable.controller;

import inu.timetable.dto.SubjectDto;
import inu.timetable.dto.SubjectFilterCriteria;
import inu.timetable.entity.Subject;
import inu.timetable.enums.SubjectType;
import inu.timetable.repository.SubjectRepository;
import inu.timetable.service.SubjectQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/subjects")
@RequiredArgsConstructor
@Slf4j
public class SubjectController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MIN_KEYWORD_LENGTH = 2;

    private final SubjectRepository subjectRepository;
    private final SubjectQueryService subjectQueryService;

    @GetMapping
    public Page<Subject> getAllSubjects(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), clampSize(size));
        return subjectRepository.findByActiveTrue(pageable);
    }

    @GetMapping("/count")
    public long getCount() {
        return subjectQueryService.countActiveSubjects();
    }

    @GetMapping("/type/{type}")
    public List<Subject> getSubjectsByType(@PathVariable SubjectType type) {
        return subjectRepository.findBySubjectTypeAndActiveTrue(type);
    }

    @GetMapping("/department/{department}")
    public List<Subject> getSubjectsByDepartment(@PathVariable String department) {
        return subjectRepository.findByDepartmentAndActiveTrue(department);
    }

    @GetMapping("/departments")
    public List<String> getAllDepartments() {
        return subjectQueryService.findDistinctDepartments();
    }

    @GetMapping("/grades")
    public List<Integer> getAllGrades() {
        return subjectQueryService.findDistinctGrades();
    }

    @GetMapping("/grade/{grade}")
    public List<Subject> getSubjectsByGrade(@PathVariable Integer grade) {
        return subjectRepository.findByGradeAndActiveTrue(grade);
    }

    @GetMapping("/professor/{professor}")
    public List<Subject> getSubjectsByProfessor(@PathVariable String professor) {
        return subjectRepository.findByProfessorAndActiveTrue(professor);
    }

    @GetMapping("/search")
    public List<SubjectDto> searchSubjects(
            @RequestParam String keyword,
            @RequestParam(required = false) Integer grade) {
        // 1글자 등 지나치게 광범위한 매칭으로 전체 결과가 반환되는 것을 막는다(미인증 접근 가능).
        if (isTooShort(keyword)) {
            return List.of();
        }
        return subjectQueryService.searchBySubjectName(keyword, grade);
    }

    @GetMapping("/search/professor")
    public List<SubjectDto> searchByProfessor(
            @RequestParam String keyword,
            @RequestParam(required = false) Integer grade) {
        if (isTooShort(keyword)) {
            return List.of();
        }
        return subjectQueryService.searchByProfessor(keyword, grade);
    }

    @GetMapping("/filter")
    public Page<SubjectDto> filterSubjects(
            @RequestParam(required = false) String semester,
            @RequestParam(required = false) String subjectName,
            @RequestParam(required = false) String professor,
            @RequestParam(required = false) String courseCode,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) List<String> departments,
            @RequestParam(required = false) String dayOfWeek,
            @RequestParam(required = false) Double startTime,
            @RequestParam(required = false) Double endTime,
            @RequestParam(required = false) SubjectType subjectType,
            @RequestParam(required = false) Integer grade,
            @RequestParam(required = false) Boolean isNight,
            @RequestParam(required = false) Boolean unassignedTime,
            @RequestParam(required = false) Integer credits,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return subjectQueryService.filterSubjects(SubjectFilterCriteria.of(
                semester, subjectName, professor, courseCode, department, departments, dayOfWeek,
                startTime, endTime, subjectType, grade, isNight, unassignedTime, credits,
                Math.max(0, page), clampSize(size)));
    }

    private int clampSize(int size) {
        return Math.max(1, Math.min(size, MAX_PAGE_SIZE));
    }

    private boolean isTooShort(String keyword) {
        return keyword == null || keyword.trim().length() < MIN_KEYWORD_LENGTH;
    }
}
