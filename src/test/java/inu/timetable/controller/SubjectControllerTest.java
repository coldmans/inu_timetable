package inu.timetable.controller;

import inu.timetable.dto.SubjectDto;
import inu.timetable.entity.Schedule;
import inu.timetable.entity.Subject;
import inu.timetable.enums.ClassMethod;
import inu.timetable.enums.SubjectType;
import inu.timetable.repository.SubjectRepository;
import inu.timetable.repository.WishlistRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubjectControllerTest {

    @org.mockito.Mock
    private SubjectRepository subjectRepository;

    @org.mockito.Mock
    private WishlistRepository wishlistRepository;

    @Test
    void filterSubjectsIncludesWishlistCount() {
        Subject popularSubject = subject(101L, "운영체제");
        Subject emptySubject = subject(102L, "자료구조");
        PageRequest pageRequest = PageRequest.of(0, 20);
        SubjectController controller = new SubjectController(subjectRepository, wishlistRepository);

        when(subjectRepository.findIdsWithFilters(
                nullable(String.class),
                nullable(String.class),
                nullable(String.class),
                anyList(),
                anyInt(),
                nullable(String.class),
                nullable(Double.class),
                nullable(Double.class),
                nullable(SubjectType.class),
                nullable(Integer.class),
                nullable(Boolean.class),
                nullable(Integer.class),
                nullable(Boolean.class),
                eq(ClassMethod.ONLINE),
                any()))
                .thenReturn(new PageImpl<>(List.of(101L, 102L), pageRequest, 2));
        when(subjectRepository.findWithSchedulesByIds(List.of(101L, 102L)))
                .thenReturn(List.of(popularSubject, emptySubject));
        when(wishlistRepository.countBySubjectIds(List.of(101L, 102L)))
                .thenReturn(List.of(count(101L, 7L)));

        Page<SubjectDto> result = controller.filterSubjects(
                null, null, null, null, null, null, null,
                null, null, null, null, null, 0, 20);

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent())
                .extracting(SubjectDto::getSubjectName, SubjectDto::getWishlistCount)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("운영체제", 7L),
                        org.assertj.core.groups.Tuple.tuple("자료구조", 0L));
    }

    private Subject subject(Long id, String subjectName) {
        Subject subject = Subject.builder()
                .active(true)
                .subjectName(subjectName)
                .credits(3)
                .professor("테스트교수")
                .department("컴퓨터공학부")
                .grade(2)
                .subjectType(SubjectType.전심)
                .classMethod(ClassMethod.OFFLINE)
                .isNight(false)
                .build();
        subject.setId(id);
        subject.getSchedules().add(Schedule.builder()
                .id(id)
                .subject(subject)
                .dayOfWeek("월")
                .startTime(1.0)
                .endTime(2.0)
                .build());
        return subject;
    }

    private WishlistRepository.SubjectWishlistCount count(Long subjectId, Long wishlistCount) {
        return new WishlistRepository.SubjectWishlistCount() {
            @Override
            public Long getSubjectId() {
                return subjectId;
            }

            @Override
            public Long getWishlistCount() {
                return wishlistCount;
            }
        };
    }
}
