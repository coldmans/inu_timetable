package inu.timetable.service;

import inu.timetable.entity.Schedule;
import inu.timetable.entity.Subject;
import inu.timetable.entity.UserNotification;
import inu.timetable.entity.UserTimetable;
import inu.timetable.repository.UserNotificationRepository;
import inu.timetable.repository.UserTimetableRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 공식 엑셀 반영으로 과목 시간이 바뀌었을 때, 그 과목을 담아둔 유저 시간표에서
 * 새 시간이 다른 과목과 겹치면 해당 과목을 시간표에서 제거하고 1회성 알림을 남긴다.
 * 겹치지 않으면 시간만 바뀐 채로 유지한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TimetableConflictResolutionService {

    private final UserTimetableRepository userTimetableRepository;
    private final UserNotificationRepository userNotificationRepository;
    private final Clock clock;

    /**
     * @param timeChangedSubjects 시간표가 변경된 과목들(스케줄은 이미 '새' 시간으로 갱신된 상태)
     * @param semester            반영 대상 학기
     * @return 시간표에서 제거된 항목 수
     */
    @Transactional
    public int resolveScheduleConflicts(List<Subject> timeChangedSubjects, String semester) {
        int removedCount = 0;
        for (Subject subject : timeChangedSubjects) {
            removedCount += resolveForSubject(subject, semester);
        }
        if (removedCount > 0) {
            log.info("시간 변경 충돌로 유저 시간표 {}건을 제거하고 알림을 생성했습니다. semester={}", removedCount, semester);
        }
        return removedCount;
    }

    private int resolveForSubject(Subject subject, String semester) {
        List<UserTimetable> entries =
                userTimetableRepository.findAllBySubjectIdAndSemesterWithUser(subject.getId(), semester);

        int removed = 0;
        for (UserTimetable entry : entries) {
            Long userId = entry.getUser().getId();
            if (hasConflictWithOtherSubjects(userId, subject, semester)) {
                userTimetableRepository.delete(entry);
                userNotificationRepository.save(UserNotification.builder()
                        .userId(userId)
                        .message(conflictMessage(subject.getSubjectName()))
                        .createdAt(LocalDateTime.now(clock))
                        .build());
                removed++;
            }
        }
        return removed;
    }

    private boolean hasConflictWithOtherSubjects(Long userId, Subject changedSubject, String semester) {
        List<UserTimetable> userEntries =
                userTimetableRepository.findByUserIdAndSemesterWithSubjectAndSchedules(userId, semester);
        return userEntries.stream()
                .map(UserTimetable::getSubject)
                .filter(other -> !other.getId().equals(changedSubject.getId()))
                .anyMatch(other -> overlaps(changedSubject.getSchedules(), other.getSchedules()));
    }

    // 같은 요일이고 newStart < otherEnd AND newEnd > otherStart 이면 겹침으로 본다.
    private boolean overlaps(List<Schedule> newSchedules, List<Schedule> otherSchedules) {
        return newSchedules.stream().anyMatch(changed -> otherSchedules.stream()
                .anyMatch(other -> changed.getDayOfWeek() != null
                        && changed.getDayOfWeek().equals(other.getDayOfWeek())
                        && changed.getStartTime() < other.getEndTime()
                        && changed.getEndTime() > other.getStartTime()));
    }

    private String conflictMessage(String subjectName) {
        return "'" + subjectName + "' 수업 시간이 변경되어 기존 시간표의 다른 과목과 겹쳐 시간표에서 제거했어요. 필요하면 다시 담아주세요.";
    }
}
