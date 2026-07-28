package inu.timetable.service;

import inu.timetable.dto.SubjectDto;
import inu.timetable.dto.SubjectFilterCriteria;
import inu.timetable.entity.Subject;
import inu.timetable.enums.SubjectType;
import inu.timetable.repository.SubjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubjectQueryService {

    private final SubjectRepository subjectRepository;
    private final SubjectFilterCacheService subjectFilterCacheService;
    private final SubjectSearchCacheService subjectSearchCacheService;

    @Cacheable(cacheNames = SubjectCacheNames.ACTIVE_SUBJECT_COUNT, key = "'active'", sync = true)
    public long countActiveSubjects() {
        return subjectRepository.countByActiveTrue();
    }

    @Cacheable(cacheNames = SubjectCacheNames.SUBJECT_DEPARTMENTS, key = "'all'", sync = true)
    public List<String> findDistinctDepartments() {
        return subjectRepository.findDistinctDepartments();
    }

    @Cacheable(
            cacheNames = SubjectCacheNames.SUBJECT_DEPARTMENTS,
            key = "#semester == null || #semester.isBlank() ? 'all' : #semester.trim()",
            sync = true)
    public List<String> findDistinctDepartments(String semester) {
        if (semester == null || semester.isBlank()) {
            return subjectRepository.findDistinctDepartments();
        }
        return subjectRepository.findDistinctDepartmentsBySemester(semester.trim());
    }

    @Cacheable(cacheNames = SubjectCacheNames.SUBJECT_GRADES, key = "'all'", sync = true)
    public List<Integer> findDistinctGrades() {
        return subjectRepository.findDistinctGrades();
    }

    public List<SubjectDto> searchBySubjectName(String keyword, Integer grade) {
        return subjectSearchCacheService.searchBySubjectName(keyword, grade);
    }

    public List<SubjectDto> searchByProfessor(String keyword, Integer grade) {
        return subjectSearchCacheService.searchByProfessor(keyword, grade);
    }

    public Page<SubjectDto> filterSubjects(SubjectFilterCriteria criteria) {
        return subjectFilterCacheService.filterSubjects(criteria).toPage();
    }

    public Page<SubjectDto> findActiveSubjects(int page, int size) {
        return filterSubjects(SubjectFilterCriteria.of(
                null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, page, size));
    }

    @Cacheable(cacheNames = SubjectCacheNames.SUBJECT_LISTINGS, key = "'type:' + #subjectType", sync = true)
    public List<SubjectDto> findBySubjectType(SubjectType subjectType) {
        return toDtos(subjectRepository.findBySubjectTypeAndActiveTrue(subjectType));
    }

    @Cacheable(cacheNames = SubjectCacheNames.SUBJECT_LISTINGS, key = "'department:' + #department", sync = true)
    public List<SubjectDto> findByDepartment(String department) {
        return toDtos(subjectRepository.findByDepartmentAndActiveTrue(department));
    }

    @Cacheable(cacheNames = SubjectCacheNames.SUBJECT_LISTINGS, key = "'grade:' + #grade", sync = true)
    public List<SubjectDto> findByGrade(Integer grade) {
        return toDtos(subjectRepository.findByGradeAndActiveTrue(grade));
    }

    @Cacheable(cacheNames = SubjectCacheNames.SUBJECT_LISTINGS, key = "'professor:' + #professor", sync = true)
    public List<SubjectDto> findByProfessor(String professor) {
        return toDtos(subjectRepository.findByProfessorAndActiveTrue(professor));
    }

    private List<SubjectDto> toDtos(List<Subject> subjects) {
        return subjects.stream()
                .map(SubjectDto::from)
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
