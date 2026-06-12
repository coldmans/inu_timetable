package inu.timetable.service;

import inu.timetable.dto.SubjectDto;
import inu.timetable.dto.SubjectFilterCriteria;
import inu.timetable.repository.SubjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubjectFilterCacheService {

    static final int MAX_CACHEABLE_PAGE_SIZE = 100;

    private final SubjectRepository subjectRepository;

    @Cacheable(
            cacheNames = SubjectCacheNames.SUBJECT_FILTERS,
            key = "#criteria",
            condition = "#criteria.size() <= " + MAX_CACHEABLE_PAGE_SIZE)
    public Page<SubjectDto> filterSubjects(SubjectFilterCriteria criteria) {
        Pageable pageable = PageRequest.of(criteria.page(), criteria.size());
        Page<Long> subjectIdPage = subjectRepository.findIdsWithFilters(
                criteria.subjectName(),
                criteria.professor(),
                criteria.department(),
                criteria.dayOfWeek(),
                criteria.startTime(),
                criteria.endTime(),
                criteria.subjectType(),
                criteria.grade(),
                criteria.isNight(),
                criteria.credits(),
                pageable);

        List<Long> subjectIds = subjectIdPage.getContent();
        if (subjectIds.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, subjectIdPage.getTotalElements());
        }

        List<SubjectDto> subjectDtos = subjectRepository.findWithSchedulesByIds(subjectIds).stream()
                .map(SubjectDto::from)
                .toList();
        return new PageImpl<>(subjectDtos, pageable, subjectIdPage.getTotalElements());
    }
}
