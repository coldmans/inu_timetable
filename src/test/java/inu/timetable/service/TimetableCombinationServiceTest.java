package inu.timetable.service;

import inu.timetable.entity.Schedule;
import inu.timetable.entity.Subject;
import inu.timetable.entity.WishlistItem;
import inu.timetable.enums.ClassMethod;
import inu.timetable.enums.SubjectType;
import inu.timetable.repository.WishlistRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TimetableCombinationServiceTest {

    private static final long USER_ID = 1L;
    private static final String SEMESTER = "2026-1";
    private static final List<String> DAYS = List.of("월", "화", "수", "목", "금");

    @Mock
    private WishlistRepository wishlistRepository;

    @ParameterizedTest
    @ValueSource(ints = {6, 12, 18, 24, 30})
    void generatesConflictFreeCombinationsAcrossWishlistSizes(int wishlistSize) {
        TimetableCombinationService service = new TimetableCombinationService(wishlistRepository);
        List<WishlistItem> wishlist = wishlist(wishlistSize, 6);
        when(wishlistRepository.findByUserIdAndSemesterWithSubjectAndSchedules(USER_ID, SEMESTER))
                .thenReturn(wishlist);

        List<List<Subject>> combinations = service.generateTimetableCombinations(
                USER_ID, SEMESTER, 18, 50, List.of());

        assertThat(combinations).isNotEmpty();
        assertThat(combinations).hasSizeLessThanOrEqualTo(50);
        assertThat(combinations).allSatisfy(combination -> {
            int totalCredits = combination.stream().mapToInt(Subject::getCredits).sum();
            assertThat(totalCredits).isBetween(15, 21);
            assertThat(hasConflict(combination)).isFalse();
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {6, 12, 18, 24, 30})
    void matchesReferenceCombinationsAcrossWishlistSizes(int wishlistSize) {
        TimetableCombinationService service = new TimetableCombinationService(wishlistRepository);
        List<WishlistItem> wishlist = wishlist(wishlistSize, 6);
        when(wishlistRepository.findByUserIdAndSemesterWithSubjectAndSchedules(USER_ID, SEMESTER))
                .thenReturn(wishlist);

        List<List<Subject>> combinations = service.generateTimetableCombinations(
                USER_ID, SEMESTER, 18, 50, List.of());

        assertThat(canonicalize(combinations))
                .containsExactlyElementsOf(canonicalize(generateReferenceCombinations(wishlist, 18, 50, List.of())));
    }

    @Test
    void matchesReferenceWhenRequiredSubjectsAndFreeDaysAreUsed() {
        TimetableCombinationService service = new TimetableCombinationService(wishlistRepository);
        Subject required = subject(1L, "필수", "월", 1.0, 2.5);
        Subject tuesday = subject(2L, "화요일", "화", 1.0, 2.5);
        Subject wednesday = subject(3L, "수요일", "수", 1.0, 2.5);
        Subject thursday = subject(4L, "목요일", "목", 1.0, 2.5);
        Subject friday = subject(5L, "금요일", "금", 1.0, 2.5);
        List<WishlistItem> wishlist = List.of(
                wishlistItem(required, true),
                wishlistItem(tuesday, false),
                wishlistItem(wednesday, false),
                wishlistItem(thursday, false),
                wishlistItem(friday, false));
        when(wishlistRepository.findByUserIdAndSemesterWithSubjectAndSchedules(USER_ID, SEMESTER))
                .thenReturn(wishlist);

        List<List<Subject>> combinations = service.generateTimetableCombinations(
                USER_ID, SEMESTER, 9, 20, List.of("금"));

        assertThat(canonicalize(combinations))
                .containsExactlyElementsOf(canonicalize(generateReferenceCombinations(wishlist, 9, 20, List.of("금"))));
    }

    @Test
    void rejectsRequiredSubjectsWhenTheyConflict() {
        TimetableCombinationService service = new TimetableCombinationService(wishlistRepository);
        Subject first = subject(1L, "필수1", "월", 1.0, 2.5);
        Subject second = subject(2L, "필수2", "월", 2.0, 3.5);
        Subject optional = subject(3L, "선택", "화", 1.0, 2.5);
        when(wishlistRepository.findByUserIdAndSemesterWithSubjectAndSchedules(USER_ID, SEMESTER))
                .thenReturn(List.of(wishlistItem(first, true), wishlistItem(second, true), wishlistItem(optional, false)));

        List<List<Subject>> combinations = service.generateTimetableCombinations(
                USER_ID, SEMESTER, 6, 20, List.of());

        assertThat(combinations).isEmpty();
    }

    @Test
    void excludesSubjectsOnRequestedFreeDays() {
        TimetableCombinationService service = new TimetableCombinationService(wishlistRepository);
        Subject monday = subject(1L, "월요일수업", "월", 1.0, 2.5);
        Subject tuesday = subject(2L, "화요일수업", "화", 1.0, 2.5);
        Subject wednesday = subject(3L, "수요일수업", "수", 1.0, 2.5);
        Subject thursday = subject(4L, "목요일수업", "목", 1.0, 2.5);
        when(wishlistRepository.findByUserIdAndSemesterWithSubjectAndSchedules(USER_ID, SEMESTER))
                .thenReturn(List.of(
                        wishlistItem(monday, false),
                        wishlistItem(tuesday, false),
                        wishlistItem(wednesday, false),
                        wishlistItem(thursday, false)));

        List<List<Subject>> combinations = service.generateTimetableCombinations(
                USER_ID, SEMESTER, 6, 20, List.of("월"));

        assertThat(combinations).isNotEmpty();
        assertThat(combinations).allSatisfy(combination -> assertThat(combination)
                .flatExtracting(Subject::getSchedules)
                .extracting(Schedule::getDayOfWeek)
                .doesNotContain("월"));
    }

    @Test
    void excludesDayOnlySchedulesOnRequestedFreeDays() {
        TimetableCombinationService service = new TimetableCombinationService(wishlistRepository);
        Subject dayOnlyMonday = subject(1L, "요일만있는월요일수업", "월", null, null);
        Subject tuesday = subject(2L, "화요일수업", "화", 1.0, 2.5);
        Subject wednesday = subject(3L, "수요일수업", "수", 1.0, 2.5);
        when(wishlistRepository.findByUserIdAndSemesterWithSubjectAndSchedules(USER_ID, SEMESTER))
                .thenReturn(List.of(
                        wishlistItem(dayOnlyMonday, false),
                        wishlistItem(tuesday, false),
                        wishlistItem(wednesday, false)));

        List<List<Subject>> combinations = service.generateTimetableCombinations(
                USER_ID, SEMESTER, 6, 20, List.of("월"));

        assertThat(combinations).isNotEmpty();
        assertThat(combinations).allSatisfy(combination -> assertThat(combination)
                .extracting(Subject::getSubjectName)
                .doesNotContain("요일만있는월요일수업"));
    }

    private List<WishlistItem> wishlist(int wishlistSize, int slotCount) {
        List<WishlistItem> items = new ArrayList<>();
        int groupSize = (int) Math.ceil((double) wishlistSize / slotCount);
        AtomicLong id = new AtomicLong(1);

        for (int index = 0; index < wishlistSize; index++) {
            int slot = Math.min(index / groupSize, slotCount - 1);
            String day = DAYS.get(slot % DAYS.size());
            double startTime = 1.0 + (slot / DAYS.size()) * 3.0;
            Subject subject = subject(
                    id.getAndIncrement(),
                    "성능테스트-%02d".formatted(index + 1),
                    day,
                    startTime,
                    startTime + 1.5);
            items.add(wishlistItem(subject, false));
        }
        return items;
    }

    private WishlistItem wishlistItem(Subject subject, boolean required) {
        return WishlistItem.builder()
                .subject(subject)
                .semester(SEMESTER)
                .priority(subject.getId().intValue())
                .isRequired(required)
                .build();
    }

    private Subject subject(Long id, String name, String day, Double startTime, Double endTime) {
        Subject subject = Subject.builder()
                .id(id)
                .courseCode("TEST-%03d".formatted(id))
                .semester(SEMESTER)
                .active(true)
                .subjectName(name)
                .credits(3)
                .professor("테스트교수")
                .subjectType(SubjectType.전심)
                .classMethod(ClassMethod.OFFLINE)
                .isNight(false)
                .grade(3)
                .department("컴퓨터공학부")
                .schedules(new ArrayList<>())
                .build();
        subject.getSchedules().add(Schedule.builder()
                .subject(subject)
                .dayOfWeek(day)
                .startTime(startTime)
                .endTime(endTime)
                .build());
        return subject;
    }

    private List<List<Subject>> generateReferenceCombinations(
            List<WishlistItem> wishlist, int targetCredits, int maxCombinations, List<String> freeDays) {
        if (wishlist.isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, WishlistItem> uniqueSubjects = new LinkedHashMap<>();
        for (WishlistItem item : wishlist) {
            String subjectName = item.getSubject().getSubjectName();
            if (!uniqueSubjects.containsKey(subjectName)) {
                uniqueSubjects.put(subjectName, item);
            }
        }

        List<WishlistItem> deduplicatedWishlist = new ArrayList<>(uniqueSubjects.values());
        List<Subject> requiredSubjects = deduplicatedWishlist.stream()
                .filter(item -> item.getIsRequired() != null && item.getIsRequired())
                .map(WishlistItem::getSubject)
                .collect(Collectors.toList());
        List<Subject> optionalSubjects = deduplicatedWishlist.stream()
                .filter(item -> item.getIsRequired() == null || !item.getIsRequired())
                .map(WishlistItem::getSubject)
                .collect(Collectors.toList());

        if (!isReferenceValidTimetable(requiredSubjects, freeDays)) {
            return new ArrayList<>();
        }

        List<List<Subject>> combinations = new ArrayList<>();
        generateReferenceCombinations(optionalSubjects, new ArrayList<>(requiredSubjects), 0,
                targetCredits, combinations, maxCombinations, freeDays);

        combinations.sort(Comparator.comparingInt(
                subjects -> Math.abs(subjects.stream().mapToInt(Subject::getCredits).sum() - targetCredits)));

        return combinations.stream().limit(maxCombinations).collect(Collectors.toList());
    }

    private void generateReferenceCombinations(List<Subject> optionalSubjects, List<Subject> current,
                                               int startIndex, int targetCredits,
                                               List<List<Subject>> combinations, int maxCombinations,
                                               List<String> freeDays) {
        if (combinations.size() >= maxCombinations) {
            return;
        }

        int currentCredits = current.stream().mapToInt(Subject::getCredits).sum();
        if (currentCredits >= targetCredits - 3 && currentCredits <= targetCredits + 3
                && isReferenceValidTimetable(current, freeDays)) {
            combinations.add(new ArrayList<>(current));
        }

        if (currentCredits > targetCredits + 3) {
            return;
        }

        for (int i = startIndex; i < optionalSubjects.size(); i++) {
            Subject subject = optionalSubjects.get(i);
            current.add(subject);
            generateReferenceCombinations(optionalSubjects, current, i + 1,
                    targetCredits, combinations, maxCombinations, freeDays);
            current.remove(current.size() - 1);
        }
    }

    private boolean isReferenceValidTimetable(List<Subject> subjects, List<String> freeDays) {
        if (hasConflict(subjects)) {
            return false;
        }

        if (freeDays != null && !freeDays.isEmpty()) {
            for (Subject subject : subjects) {
                for (Schedule schedule : subject.getSchedules()) {
                    if (freeDays.contains(schedule.getDayOfWeek())) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private List<String> canonicalize(List<List<Subject>> combinations) {
        return combinations.stream()
                .map(combination -> combination.stream()
                        .map(Subject::getCourseCode)
                        .sorted()
                        .collect(Collectors.joining("+")))
                .collect(Collectors.toList());
    }

    private boolean hasConflict(List<Subject> subjects) {
        for (int i = 0; i < subjects.size(); i++) {
            for (int j = i + 1; j < subjects.size(); j++) {
                if (hasConflict(subjects.get(i), subjects.get(j))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasConflict(Subject first, Subject second) {
        for (Schedule firstSchedule : first.getSchedules()) {
            for (Schedule secondSchedule : second.getSchedules()) {
                boolean sameDay = firstSchedule.getDayOfWeek().equals(secondSchedule.getDayOfWeek());
                boolean overlap = firstSchedule.getStartTime() < secondSchedule.getEndTime()
                        && secondSchedule.getStartTime() < firstSchedule.getEndTime();
                if (sameDay && overlap) {
                    return true;
                }
            }
        }
        return false;
    }
}
