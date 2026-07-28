package inu.timetable.service;

import inu.timetable.entity.Subject;
import inu.timetable.entity.User;
import inu.timetable.exception.ApiException;
import inu.timetable.repository.SubjectRepository;
import inu.timetable.repository.UserRepository;
import inu.timetable.repository.UserTimetableRepository;
import inu.timetable.repository.WishlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InactiveSubjectGuardTest {

    @Mock
    private WishlistRepository wishlistRepository;

    @Mock
    private UserTimetableRepository userTimetableRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SubjectRepository subjectRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private WishlistService wishlistService;
    private TimetableService timetableService;
    private User user;
    private Subject inactiveSubject;

    @BeforeEach
    void setUp() {
        wishlistService = new WishlistService(wishlistRepository, userRepository, subjectRepository);
        timetableService = new TimetableService(
                userTimetableRepository,
                userRepository,
                subjectRepository,
                eventPublisher);
        user = User.builder().id(1L).username("student").password("encoded").build();
        inactiveSubject = Subject.builder().id(10L).active(false).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(subjectRepository.findById(10L)).thenReturn(Optional.of(inactiveSubject));
    }

    @Test
    @DisplayName("미개설 과목은 위시리스트에 새로 추가할 수 없다")
    void rejectsInactiveSubjectFromWishlist() {
        assertThatThrownBy(() -> wishlistService.addToWishlist(1L, 10L, "2026-2", 3))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> {
                    ApiException apiException = (ApiException) error;
                    org.assertj.core.api.Assertions.assertThat(apiException.getStatus())
                            .isEqualTo(HttpStatus.CONFLICT);
                    org.assertj.core.api.Assertions.assertThat(apiException.getMessage())
                            .isEqualTo("현재 학기에 개설되지 않은 과목입니다.");
                });

        verify(wishlistRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("미개설 과목은 시간표에 새로 추가할 수 없다")
    void rejectsInactiveSubjectFromTimetable() {
        assertThatThrownBy(() -> timetableService.addSubjectToTimetable(
                1L, 10L, "2026-2", null))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> {
                    ApiException apiException = (ApiException) error;
                    org.assertj.core.api.Assertions.assertThat(apiException.getStatus())
                            .isEqualTo(HttpStatus.CONFLICT);
                    org.assertj.core.api.Assertions.assertThat(apiException.getMessage())
                            .isEqualTo("현재 학기에 개설되지 않은 과목입니다.");
                });

        verify(userTimetableRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
