package inu.timetable.service;

import inu.timetable.dto.SubjectDto;
import inu.timetable.dto.SubjectFilterCriteria;
import inu.timetable.repository.SubjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubjectQueryService {

    private final SubjectRepository subjectRepository;
    private final SubjectFilterCacheService subjectFilterCacheService;
    private final SubjectSearchCacheService subjectSearchCacheService;

    @Cacheable(cacheNames = SubjectCacheNames.ACTIVE_SUBJECT_COUNT, key = "'active'")
    public long countActiveSubjects() {
        return subjectRepository.countByActiveTrue();
    }

    @Cacheable(cacheNames = SubjectCacheNames.SUBJECT_DEPARTMENTS, key = "'all'")
    public List<String> findDistinctDepartments() {
        return subjectRepository.findDistinctDepartments();
    }

    @Cacheable(cacheNames = SubjectCacheNames.SUBJECT_GRADES, key = "'all'")
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
        return subjectFilterCacheService.filterSubjects(criteria);
    }
}
