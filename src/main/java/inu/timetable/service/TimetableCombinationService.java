package inu.timetable.service;

import inu.timetable.entity.Schedule;
import inu.timetable.entity.Subject;
import inu.timetable.entity.WishlistItem;
import inu.timetable.repository.WishlistRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class TimetableCombinationService {

    private static final int CREDIT_TOLERANCE = 3;
    private static final int SLOTS_PER_DAY = 128;
    private static final List<String> KNOWN_DAYS = List.of("월", "화", "수", "목", "금", "토", "일");
    
    private final WishlistRepository wishlistRepository;
    
    @Autowired
    public TimetableCombinationService(WishlistRepository wishlistRepository) {
        this.wishlistRepository = wishlistRepository;
    }
    
    public List<List<Subject>> generateTimetableCombinations(Long userId, String semester, int targetCredits, int maxCombinations) {
        return generateTimetableCombinations(userId, semester, targetCredits, maxCombinations, new ArrayList<>());
    }

    public List<List<Subject>> generateTimetableCombinations(Long userId, String semester, int targetCredits, int maxCombinations, List<String> freeDays) {
        // 위시리스트 가져오기
        List<WishlistItem> wishlist = wishlistRepository.findByUserIdAndSemesterWithSubjectAndSchedules(userId, semester);

        if (wishlist.isEmpty()) {
            return new ArrayList<>();
        }

        // 과목명별로 그룹화하여 중복 제거.
        // 같은 과목명 그룹 안에 '필수(isRequired)' 분반이 있으면 그 분반을 우선 보존한다.
        // (priority 가 앞선 선택 분반이 필수 분반을 덮어 필수 플래그가 소실되는 문제 방지)
        Map<String, WishlistItem> uniqueSubjects = new LinkedHashMap<>();
        for (WishlistItem item : wishlist) {
            String subjectName = item.getSubject().getSubjectName();
            WishlistItem existing = uniqueSubjects.get(subjectName);
            if (existing == null) {
                uniqueSubjects.put(subjectName, item);
            } else if (Boolean.TRUE.equals(item.getIsRequired())
                    && !Boolean.TRUE.equals(existing.getIsRequired())) {
                uniqueSubjects.put(subjectName, item);
            }
        }

        List<WishlistItem> deduplicatedWishlist = new ArrayList<>(uniqueSubjects.values());

        // 필수 과목과 선택 과목 분리
        List<Subject> requiredSubjects = deduplicatedWishlist.stream()
            .filter(item -> item.getIsRequired() != null && item.getIsRequired())
            .map(WishlistItem::getSubject)
            .collect(Collectors.toList());

        List<Subject> optionalSubjects = deduplicatedWishlist.stream()
            .filter(item -> item.getIsRequired() == null || !item.getIsRequired())
            .map(WishlistItem::getSubject)
            .collect(Collectors.toList());

        Set<String> freeDaySet = freeDays == null ? Set.of() : new HashSet<>(freeDays);
        TimeSlotEncoder timeSlotEncoder = new TimeSlotEncoder();
        List<SubjectOption> requiredOptions = requiredSubjects.stream()
                .map(subject -> SubjectOption.from(subject, timeSlotEncoder))
                .toList();
        List<SubjectOption> optionalOptions = optionalSubjects.stream()
                .map(subject -> SubjectOption.from(subject, timeSlotEncoder))
                .toList();

        BitSet requiredTimeMask = new BitSet();
        int requiredCredits = 0;
        for (SubjectOption requiredOption : requiredOptions) {
            if (requiredOption.hasFreeDayConflict(freeDaySet)
                    || requiredOption.hasTimeConflict(requiredTimeMask)) {
                return new ArrayList<>(); // 필수 과목들끼리 시간이 겹치거나 공강 요일을 위반하면 조합 생성 불가
            }
            requiredTimeMask.or(requiredOption.timeMask());
            requiredCredits += requiredOption.credits();
        }

        List<List<Subject>> combinations = new ArrayList<>();

        // 필수 과목을 먼저 포함한 상태로 조합 생성
        generateCombinationsWithRequired(optionalOptions, new ArrayList<>(requiredSubjects), 0,
                requiredCredits, requiredTimeMask, targetCredits, combinations, maxCombinations,
                freeDaySet, suffixCredits(optionalOptions));

        // 필수 과목 학점 합이 목표+tolerance 를 넘어 유효 조합이 하나도 없으면,
        // 필수 과목만으로 구성된 시간표를 최소 1개 보장한다(필수는 반드시 포함되어야 하므로).
        // 필수끼리의 시간 충돌은 위에서 이미 걸러졌으므로 여기 도달하면 유효한 조합이다.
        if (combinations.isEmpty() && !requiredSubjects.isEmpty()) {
            combinations.add(new ArrayList<>(requiredSubjects));
        }

        // 학점 기준으로 정렬 (목표 학점에 가까운 순)
        combinations.sort((a, b) -> {
            int creditsA = a.stream().mapToInt(Subject::getCredits).sum();
            int creditsB = b.stream().mapToInt(Subject::getCredits).sum();

            int diffA = Math.abs(creditsA - targetCredits);
            int diffB = Math.abs(creditsB - targetCredits);

            return Integer.compare(diffA, diffB);
        });

        return combinations.stream().limit(maxCombinations).collect(Collectors.toList());
    }
    
    private void generateCombinationsWithRequired(List<SubjectOption> optionalSubjects, List<Subject> current,
                                                int startIndex, int currentCredits, BitSet currentTimeMask,
                                                int targetCredits, List<List<Subject>> combinations,
                                                int maxCombinations, Set<String> freeDays, int[] suffixCredits) {

        if (combinations.size() >= maxCombinations) {
            return;
        }

        int minCredits = targetCredits - CREDIT_TOLERANCE;
        int maxCredits = targetCredits + CREDIT_TOLERANCE;

        // 목표 학점 달성 또는 근사치 도달 시 조합 추가.
        // 과목 0개(빈 시간표)는 targetCredits 가 작아 minCredits<=0 이 되는 경우에도 추천하지 않는다.
        if (!current.isEmpty() && currentCredits >= minCredits && currentCredits <= maxCredits) {
            combinations.add(new ArrayList<>(current));
        }

        // 학점이 목표보다 너무 크면 중단
        if (currentCredits >= maxCredits) {
            return;
        }

        if (startIndex >= optionalSubjects.size() || currentCredits + suffixCredits[startIndex] < minCredits) {
            return;
        }

        // 선택 과목들을 재귀적으로 추가
        for (int i = startIndex; i < optionalSubjects.size(); i++) {
            SubjectOption option = optionalSubjects.get(i);
            if (option.hasFreeDayConflict(freeDays)
                    || option.hasTimeConflict(currentTimeMask)
                    || currentCredits + option.credits() > maxCredits) {
                continue;
            }

            current.add(option.subject());
            BitSet nextTimeMask = (BitSet) currentTimeMask.clone();
            nextTimeMask.or(option.timeMask());
            generateCombinationsWithRequired(optionalSubjects, current, i + 1,
                    currentCredits + option.credits(), nextTimeMask, targetCredits, combinations,
                    maxCombinations, freeDays, suffixCredits);
            current.remove(current.size() - 1);

            if (combinations.size() >= maxCombinations) {
                return;
            }
        }
    }

    private int[] suffixCredits(List<SubjectOption> optionalSubjects) {
        int[] suffixCredits = new int[optionalSubjects.size() + 1];
        for (int i = optionalSubjects.size() - 1; i >= 0; i--) {
            suffixCredits[i] = suffixCredits[i + 1] + optionalSubjects.get(i).credits();
        }
        return suffixCredits;
    }
    
    public Map<String, Object> getTimetableStatistics(List<Subject> subjects) {
        Map<String, Object> stats = new HashMap<>();

        int totalCredits = subjects.stream().mapToInt(Subject::getCredits).sum();

        Map<String, Long> subjectTypeCount = subjects.stream()
            .collect(Collectors.groupingBy(
                s -> s.getSubjectType().toString(),
                Collectors.counting()
            ));

        // 요일별 수업 개수 계산
        Map<String, Long> dayCount = subjects.stream()
            .flatMap(subject -> subject.getSchedules().stream())
            .collect(Collectors.groupingBy(
                Schedule::getDayOfWeek,
                Collectors.counting()
            ));

        // 공강 요일 계산 (월~금 중 수업이 없는 요일)
        List<String> allDays = List.of("월", "화", "수", "목", "금");
        List<String> actualFreeDays = allDays.stream()
            .filter(day -> !dayCount.containsKey(day))
            .collect(Collectors.toList());

        stats.put("totalCredits", totalCredits);
        stats.put("subjectCount", subjects.size());
        stats.put("subjectTypeDistribution", subjectTypeCount);
        stats.put("dayDistribution", dayCount);
        stats.put("freeDays", actualFreeDays);

        return stats;
    }

    private record SubjectOption(Subject subject, int credits, BitSet timeMask, Set<String> classDays) {

        private static SubjectOption from(Subject subject, TimeSlotEncoder timeSlotEncoder) {
            BitSet timeMask = new BitSet();
            Set<String> classDays = new HashSet<>();
            for (Schedule schedule : subject.getSchedules()) {
                if (schedule.getDayOfWeek() != null) {
                    classDays.add(schedule.getDayOfWeek());
                }
                if (schedule.getDayOfWeek() == null
                        || schedule.getStartTime() == null
                        || schedule.getEndTime() == null) {
                    continue;
                }
                int dayIndex = timeSlotEncoder.dayIndex(schedule.getDayOfWeek());
                int startSlot = (int) Math.round(schedule.getStartTime() * 2);
                int endSlot = (int) Math.round(schedule.getEndTime() * 2);
                for (int slot = startSlot; slot < endSlot; slot++) {
                    timeMask.set(dayIndex * SLOTS_PER_DAY + slot);
                }
            }
            return new SubjectOption(subject, subject.getCredits(), timeMask, classDays);
        }

        private boolean hasTimeConflict(BitSet currentTimeMask) {
            return currentTimeMask.intersects(timeMask);
        }

        private boolean hasFreeDayConflict(Set<String> freeDays) {
            return !freeDays.isEmpty() && classDays.stream().anyMatch(freeDays::contains);
        }
    }

    private static class TimeSlotEncoder {
        private final Map<String, Integer> dayIndexes = new HashMap<>();

        private TimeSlotEncoder() {
            for (int i = 0; i < KNOWN_DAYS.size(); i++) {
                dayIndexes.put(KNOWN_DAYS.get(i), i);
            }
        }

        private int dayIndex(String dayOfWeek) {
            return dayIndexes.computeIfAbsent(dayOfWeek, ignored -> dayIndexes.size());
        }
    }
}
