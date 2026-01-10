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

        // 과목명별로 그룹화하여 중복 제거 (같은 과목명 중 첫 번째만 선택)
        Map<String, WishlistItem> uniqueSubjects = new LinkedHashMap<>();
        for (WishlistItem item : wishlist) {
            String subjectName = item.getSubject().getSubjectName();
            if (!uniqueSubjects.containsKey(subjectName)) {
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

        // 필수 과목들이 시간 겹침 없이 배치 가능한지 먼저 확인
        if (!isValidTimetable(requiredSubjects, freeDays)) {
            return new ArrayList<>(); // 필수 과목들끼리 시간이 겹치거나 공강 요일을 위반하면 조합 생성 불가
        }

        List<List<Subject>> combinations = new ArrayList<>();

        // 필수 과목을 먼저 포함한 상태로 조합 생성
        generateCombinationsWithRequired(requiredSubjects, optionalSubjects, new ArrayList<>(requiredSubjects),
                                       0, targetCredits, combinations, maxCombinations, freeDays);

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
    
    private void generateCombinationsWithRequired(List<Subject> requiredSubjects, List<Subject> optionalSubjects,
                                                List<Subject> current, int startIndex, int targetCredits,
                                                List<List<Subject>> combinations, int maxCombinations, List<String> freeDays) {

        if (combinations.size() >= maxCombinations) {
            return;
        }

        int currentCredits = current.stream().mapToInt(Subject::getCredits).sum();

        // 목표 학점 달성 또는 근사치 도달 시 조합 추가
        if (currentCredits >= targetCredits - 3 && currentCredits <= targetCredits + 3) {
            if (isValidTimetable(current, freeDays)) {
                combinations.add(new ArrayList<>(current));
            }
        }

        // 학점이 목표보다 너무 크면 중단
        if (currentCredits > targetCredits + 3) {
            return;
        }

        // 선택 과목들을 재귀적으로 추가
        for (int i = startIndex; i < optionalSubjects.size(); i++) {
            Subject subject = optionalSubjects.get(i);
            current.add(subject);
            generateCombinationsWithRequired(requiredSubjects, optionalSubjects, current, i + 1,
                                           targetCredits, combinations, maxCombinations, freeDays);
            current.remove(current.size() - 1);
        }
    }

    private void generateCombinations(List<Subject> subjects, List<Subject> current, int startIndex, 
                                     int targetCredits, List<List<Subject>> combinations, int maxCombinations) {
        
        if (combinations.size() >= maxCombinations) {
            return;
        }
        
        int currentCredits = current.stream().mapToInt(Subject::getCredits).sum();
        
        // 목표 학점 달성 또는 근사치 도달 시 조합 추가
        if (currentCredits >= targetCredits - 3 && currentCredits <= targetCredits + 3) {
            if (isValidTimetable(current)) {
                combinations.add(new ArrayList<>(current));
            }
        }
        
        // 학점이 목표보다 너무 크면 중단
        if (currentCredits > targetCredits + 3) {
            return;
        }
        
        // 재귀적으로 과목 추가
        for (int i = startIndex; i < subjects.size(); i++) {
            Subject subject = subjects.get(i);
            current.add(subject);
            generateCombinations(subjects, current, i + 1, targetCredits, combinations, maxCombinations);
            current.remove(current.size() - 1);
        }
    }
    
    private boolean isValidTimetable(List<Subject> subjects) {
        return isValidTimetable(subjects, new ArrayList<>());
    }

    private boolean isValidTimetable(List<Subject> subjects, List<String> freeDays) {
        // 모든 과목 쌍에 대해 시간 겹침 체크
        for (int i = 0; i < subjects.size(); i++) {
            for (int j = i + 1; j < subjects.size(); j++) {
                if (hasTimeConflict(subjects.get(i), subjects.get(j))) {
                    return false;
                }
            }
        }

        // 공강 요일 체크
        if (freeDays != null && !freeDays.isEmpty()) {
            for (Subject subject : subjects) {
                for (Schedule schedule : subject.getSchedules()) {
                    if (freeDays.contains(schedule.getDayOfWeek())) {
                        return false; // 공강 요일에 수업이 있으면 invalid
                    }
                }
            }
        }

        return true;
    }
    
    private boolean hasTimeConflict(Subject subject1, Subject subject2) {
        for (Schedule s1 : subject1.getSchedules()) {
            for (Schedule s2 : subject2.getSchedules()) {
                if (isScheduleConflict(s1, s2)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private boolean isScheduleConflict(Schedule s1, Schedule s2) {
        // 같은 요일이 아니면 겹치지 않음
        if (!s1.getDayOfWeek().equals(s2.getDayOfWeek())) {
            return false;
        }
        
        // 시간 겹침 체크
        double start1 = s1.getStartTime();
        double end1 = s1.getEndTime();
        double start2 = s2.getStartTime();
        double end2 = s2.getEndTime();
        
        // 겹치지 않는 경우: s1이 s2보다 완전히 이전이거나 완전히 이후
        return !(end1 <= start2 || end2 <= start1);
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
}