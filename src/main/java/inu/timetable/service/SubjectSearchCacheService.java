package inu.timetable.service;

import inu.timetable.dto.SubjectDto;
import inu.timetable.dto.SubjectSearchCriteria;
import inu.timetable.entity.Subject;
import inu.timetable.repository.SubjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubjectSearchCacheService {

    private final SubjectRepository subjectRepository;

    @Cacheable(
            cacheNames = SubjectCacheNames.SUBJECT_NAME_SEARCH,
            key = "T(inu.timetable.dto.SubjectSearchCriteria).of(#keyword, #grade)")
    public List<SubjectDto> searchBySubjectName(String keyword, Integer grade) {
        SubjectSearchCriteria criteria = SubjectSearchCriteria.of(keyword, grade);
        List<Subject> subjects = grade == null
                ? subjectRepository.findBySubjectNameContainingAndActiveTrue(criteria.keyword())
                : subjectRepository.findBySubjectNameContainingAndGradeAndActiveTrue(criteria.keyword(), criteria.grade());
        return subjects.stream()
                .map(SubjectDto::from)
                .toList();
    }

    @Cacheable(
            cacheNames = SubjectCacheNames.SUBJECT_PROFESSOR_SEARCH,
            key = "T(inu.timetable.dto.SubjectSearchCriteria).of(#keyword, #grade)")
    public List<SubjectDto> searchByProfessor(String keyword, Integer grade) {
        SubjectSearchCriteria criteria = SubjectSearchCriteria.of(keyword, grade);
        List<Subject> subjects = grade == null
                ? subjectRepository.findByProfessorContainingAndActiveTrue(criteria.keyword())
                : subjectRepository.findByProfessorContainingAndGradeAndActiveTrue(criteria.keyword(), criteria.grade());
        return subjects.stream()
                .map(SubjectDto::from)
                .toList();
    }
}
