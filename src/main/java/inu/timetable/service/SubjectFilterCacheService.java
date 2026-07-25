package inu.timetable.service;

import inu.timetable.dto.SubjectDto;
import inu.timetable.dto.SubjectFilterCriteria;
import inu.timetable.entity.Subject;
import inu.timetable.enums.ClassMethod;
import inu.timetable.repository.SubjectRepository;
import inu.timetable.repository.UserTimetableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubjectFilterCacheService {

    static final int MAX_CACHEABLE_PAGE_SIZE = 100;

    private final SubjectRepository subjectRepository;
    private final UserTimetableRepository userTimetableRepository;

    @Cacheable(
            cacheNames = SubjectCacheNames.SUBJECT_FILTERS,
            key = "#criteria",
            condition = "#criteria.size() <= " + MAX_CACHEABLE_PAGE_SIZE,
            sync = true)
    public Page<SubjectDto> filterSubjects(SubjectFilterCriteria criteria) {
        Pageable pageable = PageRequest.of(criteria.page(), criteria.size());
        List<String> departments = criteria.departments();
        List<String> departmentListParam = departments.isEmpty()
                ? List.of("__unused_department__")
                : departments;
        Page<Long> subjectIdPage = subjectRepository.findIdsWithFilters(
                criteria.semester(),
                criteria.subjectName(),
                criteria.professor(),
                criteria.department(),
                departmentListParam,
                departments.size(),
                criteria.dayOfWeek(),
                criteria.startTime(),
                criteria.endTime(),
                criteria.subjectType(),
                criteria.grade(),
                criteria.isNight(),
                criteria.credits(),
                criteria.unassignedTime(),
                ClassMethod.ONLINE,
                pageable);

        List<Long> subjectIds = subjectIdPage.getContent();
        if (subjectIds.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, subjectIdPage.getTotalElements());
        }

        List<Subject> subjects = new ArrayList<>(subjectRepository.findWithSchedulesByIds(subjectIds));
        Map<Long, Integer> subjectOrder = IntStream.range(0, subjectIds.size())
                .boxed()
                .collect(Collectors.toMap(subjectIds::get, index -> index));
        subjects.sort(Comparator.comparingInt(
                subject -> subjectOrder.getOrDefault(subject.getId(), Integer.MAX_VALUE)));

        Map<Long, Long> timetableAddCounts = userTimetableRepository.countAddedUsersBySubjectIds(subjectIds).stream()
                .collect(Collectors.toMap(
                        UserTimetableRepository.SubjectTimetableAddCount::getSubjectId,
                        UserTimetableRepository.SubjectTimetableAddCount::getTimetableAddCount));

        List<SubjectDto> subjectDtos = subjects.stream()
                .map(subject -> SubjectDto.from(subject, timetableAddCounts.getOrDefault(subject.getId(), 0L)))
                .toList();
        return new PageImpl<>(subjectDtos, pageable, subjectIdPage.getTotalElements());
    }
}
