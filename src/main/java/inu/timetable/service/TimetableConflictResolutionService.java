package inu.timetable.service;

import inu.timetable.entity.Schedule;
import inu.timetable.entity.Subject;
import inu.timetable.entity.UserNotification;
import inu.timetable.entity.UserTimetable;
import inu.timetable.repository.UserNotificationRepository;
import inu.timetable.repository.UserTimetableRepository;
import inu.timetable.repository.WishlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 공식 과목 데이터 변경을 유저 시간표와 위시리스트에 안전하게 반영한다.
 * 변경된 시간 때문에 충돌하는 과목은 시간표에서 제거하고, 변경 영향을 받은 유저에게
 * 로그인 후 한 번 확인할 수 있는 알림을 남긴다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TimetableConflictResolutionService {

    static final String SUBJECT_CHANGE_NOTICE =
            "강의계획서가 변경되어 충돌이 생겼을 경우 과목은 시간표에서 제외되었습니다.";

    private final UserTimetableRepository userTimetableRepository;
    private final WishlistRepository wishlistRepository;
    private final UserNotificationRepository userNotificationRepository;
    private final Clock clock;

    /**
     * 스케줄은 이미 새 값으로 갱신되고, 폐강 과목은 active=false가 된 상태에서 호출한다.
     *
     * <p>충돌 판정은 삭제 전 시간표 스냅샷을 기준으로 한 번에 수행한다. 따라서 변경된 두 과목이
     * 서로 충돌하면 처리 순서와 관계없이 두 과목을 모두 제외해 결과 시간표에 충돌이 남지 않는다.</p>
     */
    @Transactional
    public ReconciliationResult reconcileImportChanges(
            List<Subject> modifiedSubjects,
            List<Subject> timeChangedSubjects,
            List<Subject> deactivatedSubjects,
            String semester) {
        List<Long> modifiedSubjectIds = subjectIds(modifiedSubjects);
        List<Long> timeChangedSubjectIds = subjectIds(timeChangedSubjects);
        List<Long> deactivatedSubjectIds = subjectIds(deactivatedSubjects);

        Set<Long> affectedSubjectIds = new LinkedHashSet<>(modifiedSubjectIds);
        affectedSubjectIds.addAll(deactivatedSubjectIds);
        List<Long> affectedIds = new ArrayList<>(affectedSubjectIds);
        Set<Long> timetableUserIds = findTimetableUserIds(affectedIds, semester);
        Set<Long> wishlistUserIds = findWishlistUserIds(affectedIds, semester);
        Set<Long> affectedUserIds = new LinkedHashSet<>(timetableUserIds);
        affectedUserIds.addAll(wishlistUserIds);

        List<RemovedTimetableEntry> deactivatedEntries =
                snapshotDeactivatedEntries(deactivatedSubjectIds, semester);
        if (!deactivatedSubjectIds.isEmpty()) {
            userTimetableRepository.deleteAllBySubjectIdsAndSemester(deactivatedSubjectIds, semester);
        }
        // 없어질 과목과의 겹침 때문에 정상 개설 과목까지 함께 빠지는 일을 막기 위해
        // 미개설 항목을 먼저 제거한 뒤 남은 시간표에서 충돌을 판정한다.
        List<RemovedTimetableEntry> conflictEntries =
                removeConflictingChangedEntries(timeChangedSubjectIds, semester);
        int notifiedUserCount = createNotifications(affectedUserIds);

        if (!affectedUserIds.isEmpty()) {
            log.info(
                    "공식 과목 변경을 유저 데이터에 반영했습니다. semester={}, affectedUsers={}, "
                            + "conflictRemoved={}, deactivatedRemoved={}, notifications={}",
                    semester,
                    affectedUserIds.size(),
                    conflictEntries.size(),
                    deactivatedEntries.size(),
                    notifiedUserCount);
        }

        List<RemovedTimetableEntry> removedEntries = new ArrayList<>(deactivatedEntries);
        removedEntries.addAll(conflictEntries);
        return new ReconciliationResult(
                conflictEntries.size(),
                deactivatedEntries.size(),
                notifiedUserCount,
                timetableUserIds.size(),
                wishlistUserIds.size(),
                affectedUserIds.size(),
                removedEntries);
    }

    /**
     * 기존 호출부 호환용. 시간 변경 과목을 전체 변경 과목으로 간주한다.
     */
    @Transactional
    public int resolveScheduleConflicts(List<Subject> timeChangedSubjects, String semester) {
        return reconcileImportChanges(
                timeChangedSubjects,
                timeChangedSubjects,
                List.of(),
                semester).conflictRemovedCount();
    }

    private Set<Long> findTimetableUserIds(List<Long> affectedSubjectIds, String semester) {
        if (affectedSubjectIds.isEmpty()) {
            return Set.of();
        }
        return new LinkedHashSet<>(userTimetableRepository
                .findDistinctUserIdsBySubjectIdsAndSemester(affectedSubjectIds, semester));
    }

    private Set<Long> findWishlistUserIds(List<Long> affectedSubjectIds, String semester) {
        if (affectedSubjectIds.isEmpty()) {
            return Set.of();
        }
        return new LinkedHashSet<>(wishlistRepository
                .findDistinctUserIdsBySubjectIdsAndSemester(affectedSubjectIds, semester));
    }

    private List<RemovedTimetableEntry> snapshotDeactivatedEntries(
            List<Long> subjectIds,
            String semester) {
        if (subjectIds.isEmpty()) {
            return List.of();
        }
        return userTimetableRepository
                .findAllBySubjectIdsAndSemesterWithUserAndSubject(subjectIds, semester)
                .stream()
                .map(entry -> toRemovedEntry(entry, "CANCELED"))
                .toList();
    }

    private List<RemovedTimetableEntry> removeConflictingChangedEntries(
            List<Long> timeChangedSubjectIds,
            String semester) {
        if (timeChangedSubjectIds.isEmpty()) {
            return List.of();
        }

        Set<Long> changedSubjectIds = Set.copyOf(timeChangedSubjectIds);
        List<Long> candidateUserIds = userTimetableRepository
                .findDistinctUserIdsBySubjectIdsAndSemester(timeChangedSubjectIds, semester);
        List<UserTimetable> entriesToRemove = new ArrayList<>();

        for (Long userId : candidateUserIds) {
            List<UserTimetable> snapshot =
                    userTimetableRepository.findByUserIdAndSemesterWithSubjectAndSchedules(userId, semester);
            for (UserTimetable changedEntry : snapshot) {
                if (!changedSubjectIds.contains(changedEntry.getSubject().getId())) {
                    continue;
                }
                boolean hasConflict = snapshot.stream()
                        .filter(otherEntry -> !Objects.equals(otherEntry.getId(), changedEntry.getId()))
                        .anyMatch(otherEntry -> overlaps(
                                changedEntry.getSubject().getSchedules(),
                                otherEntry.getSubject().getSchedules()));
                if (hasConflict) {
                    entriesToRemove.add(changedEntry);
                }
            }
        }

        List<RemovedTimetableEntry> removedEntries = entriesToRemove.stream()
                .map(entry -> toRemovedEntry(entry, "TIME_CONFLICT"))
                .toList();
        userTimetableRepository.deleteAll(entriesToRemove);
        return removedEntries;
    }

    private RemovedTimetableEntry toRemovedEntry(UserTimetable entry, String reason) {
        Subject subject = entry.getSubject();
        return new RemovedTimetableEntry(
                entry.getId(),
                entry.getUser().getId(),
                subject.getId(),
                subject.getCourseCode(),
                subject.getSubjectName(),
                reason);
    }

    private int createNotifications(Set<Long> affectedUserIds) {
        if (affectedUserIds.isEmpty()) {
            return 0;
        }

        List<Long> userIds = new ArrayList<>(affectedUserIds);
        Set<Long> alreadyNotified = Set.copyOf(userNotificationRepository
                .findUserIdsWithUnreadMessage(userIds, SUBJECT_CHANGE_NOTICE));
        LocalDateTime createdAt = LocalDateTime.now(clock);
        List<UserNotification> notifications = userIds.stream()
                .filter(userId -> !alreadyNotified.contains(userId))
                .map(userId -> UserNotification.builder()
                        .userId(userId)
                        .message(SUBJECT_CHANGE_NOTICE)
                        .createdAt(createdAt)
                        .build())
                .toList();
        userNotificationRepository.saveAll(notifications);
        return notifications.size();
    }

    private List<Long> subjectIds(List<Subject> subjects) {
        if (subjects == null || subjects.isEmpty()) {
            return List.of();
        }
        return subjects.stream()
                .filter(Objects::nonNull)
                .map(Subject::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    // 같은 요일이고 newStart < otherEnd AND newEnd > otherStart 이면 겹침으로 본다.
    private boolean overlaps(List<Schedule> newSchedules, List<Schedule> otherSchedules) {
        if (newSchedules == null || otherSchedules == null) {
            return false;
        }
        return newSchedules.stream().anyMatch(changed -> otherSchedules.stream()
                .anyMatch(other -> overlaps(changed, other)));
    }

    private boolean overlaps(Schedule changed, Schedule other) {
        if (changed == null
                || other == null
                || changed.getDayOfWeek() == null
                || changed.getStartTime() == null
                || changed.getEndTime() == null
                || other.getDayOfWeek() == null
                || other.getStartTime() == null
                || other.getEndTime() == null
                || !changed.getDayOfWeek().equals(other.getDayOfWeek())) {
            return false;
        }

        double changedStart = changed.getStartTime();
        double changedEnd = changed.getEndTime();
        double otherStart = other.getStartTime();
        double otherEnd = other.getEndTime();
        if (changedStart > changedEnd) {
            changedEnd += 8.0;
        }
        if (otherStart > otherEnd) {
            otherEnd += 8.0;
        }
        return changedStart < otherEnd && changedEnd > otherStart;
    }

    public record ReconciliationResult(
            int conflictRemovedCount,
            int deactivatedRemovedCount,
            int notifiedUserCount,
            int timetableUserCount,
            int wishlistUserCount,
            int affectedUserCount,
            List<RemovedTimetableEntry> removedEntries) {
    }

    public record RemovedTimetableEntry(
            Long timetableEntryId,
            Long userId,
            Long subjectId,
            String courseCode,
            String subjectName,
            String reason) {
    }
}
