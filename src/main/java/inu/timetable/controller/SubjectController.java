package inu.timetable.controller;

import inu.timetable.entity.Subject;
import inu.timetable.enums.ClassMethod;
import inu.timetable.enums.SubjectType;
import inu.timetable.repository.SubjectRepository;
import inu.timetable.dto.SubjectDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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
        return subjectRepository.findByActiveTrue(pageable);
    }

    @GetMapping("/count")
    public long getCount() {
        return subjectRepository.countByActiveTrue();
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
        return subjectRepository.findDistinctDepartments();
    }

    @GetMapping("/grades")
    public List<Integer> getAllGrades() {
        return subjectRepository.findDistinctGrades();
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
        List<Subject> subjects;
        if (grade != null) {
            subjects = subjectRepository.findBySubjectNameContainingAndGradeAndActiveTrue(keyword, grade);
        } else {
            subjects = subjectRepository.findBySubjectNameContainingAndActiveTrue(keyword);
        }
        return subjects.stream()
                .map(SubjectDto::from)
                .collect(Collectors.toList());
    }

    @GetMapping("/search/professor")
    public List<SubjectDto> searchByProfessor(
            @RequestParam String keyword,
            @RequestParam(required = false) Integer grade) {
        List<Subject> subjects;
        if (grade != null) {
            subjects = subjectRepository.findByProfessorContainingAndGradeAndActiveTrue(keyword, grade);
        } else {
            subjects = subjectRepository.findByProfessorContainingAndActiveTrue(keyword);
        }
        return subjects.stream()
                .map(SubjectDto::from)
                .collect(Collectors.toList());
    }

    @GetMapping("/filter")
    public Page<SubjectDto> filterSubjects(
            @RequestParam(required = false) String subjectName,
            @RequestParam(required = false) String professor,
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

        System.out.println(">>> [API] Received request for /filter with page=" + page + ", size=" + size);

        Pageable pageable = PageRequest.of(page, size);
        String normalizedDepartment = normalizeDepartment(department);
        List<String> normalizedDepartments = normalizeDepartments(departments);
        List<String> departmentListParam = normalizedDepartments.isEmpty()
                ? Collections.singletonList("__unused_department__")
                : normalizedDepartments;

        // 1단계: 필터로 과목 ID 조회 (페이지네이션 적용)
        Page<Long> subjectIdPage = subjectRepository.findIdsWithFilters(
                subjectName, professor, normalizedDepartment, departmentListParam, normalizedDepartments.size(), dayOfWeek,
                startTime, endTime, subjectType, grade, isNight, credits,
                unassignedTime, ClassMethod.ONLINE, pageable);

        // 2단계: 조회된 ID로 과목 상세 정보와 시간표를 함께 조회
        List<Long> subjectIds = subjectIdPage.getContent();
        System.out.println(">>> [DB] Found " + subjectIds.size() + " subject IDs for this page.");

        if (subjectIds.isEmpty()) {
            System.out.println(">>> [API] Returning empty page.");
            return new org.springframework.data.domain.PageImpl<>(new java.util.ArrayList<>(), pageable,
                    subjectIdPage.getTotalElements());
        }

        List<Subject> subjectsWithSchedules = subjectRepository.findWithSchedulesByIds(subjectIds);
        System.out.println(">>> [API] Returning page with " + subjectsWithSchedules.size() + " subjects.");

        // DTO로 변환
        List<SubjectDto> subjectDtos = subjectsWithSchedules.stream()
                .map(SubjectDto::from)
                .collect(Collectors.toList());

        // Page 객체는 유지하되, 내용물만 교체
        return new org.springframework.data.domain.PageImpl<>(subjectDtos, pageable, subjectIdPage.getTotalElements());
    }

    private String normalizeDepartment(String department) {
        if (department == null || department.isBlank() || "전체".equals(department)) {
            return null;
        }

        return department.trim();
    }

    private List<String> normalizeDepartments(List<String> departments) {
        if (departments == null) {
            return Collections.emptyList();
        }

        return departments.stream()
                .filter(Objects::nonNull)
                .flatMap(value -> Arrays.stream(value.split(",")))
                .map(String::trim)
                .filter(value -> !value.isBlank() && !"전체".equals(value))
                .distinct()
                .collect(Collectors.toList());
    }

}
