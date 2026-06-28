package inu.timetable.service;

import inu.timetable.entity.Subject;
import inu.timetable.entity.User;
import inu.timetable.entity.UserTimetable;
import inu.timetable.exception.ApiException;
import inu.timetable.repository.SubjectRepository;
import inu.timetable.repository.UserRepository;
import inu.timetable.repository.UserTimetableRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TimetableService {
    
    private final UserTimetableRepository userTimetableRepository;
    private final UserRepository userRepository;
    private final SubjectRepository subjectRepository;
    
    @Autowired
    public TimetableService(UserTimetableRepository userTimetableRepository, 
                           UserRepository userRepository,
                           SubjectRepository subjectRepository) {
        this.userTimetableRepository = userTimetableRepository;
        this.userRepository = userRepository;
        this.subjectRepository = subjectRepository;
    }
    
    @Transactional
    public UserTimetable addSubjectToTimetable(Long userId, Long subjectId, String semester, String memo) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> ApiException.notFound("사용자를 찾을 수 없습니다."));
            
        Subject subject = subjectRepository.findById(subjectId)
            .orElseThrow(() -> ApiException.notFound("과목을 찾을 수 없습니다."));
        
        // 같은 학기에 이미 추가된 과목인지 확인(다른 학기에는 같은 과목을 추가할 수 있다)
        if (userTimetableRepository.existsByUserIdAndSubjectIdAndSemester(userId, subjectId, semester)) {
            throw ApiException.conflict("이미 시간표에 추가된 과목입니다.");
        }
        
        // 시간표 겹침 확인
        List<UserTimetable> currentTimetable = userTimetableRepository.findByUserIdAndSemesterWithSubjectAndSchedules(userId, semester);
        if (hasTimeConflict(currentTimetable, subject)) {
            throw ApiException.conflict("시간표가 겹치는 과목이 있습니다.");
        }
        
        UserTimetable userTimetable = UserTimetable.builder()
            .user(user)
            .subject(subject)
            .semester(semester)
            .memo(memo)
            .build();
            
        return userTimetableRepository.save(userTimetable);
    }
    
    @Transactional
    public void removeSubjectFromTimetable(Long userId, Long subjectId) {
        int deleted = userTimetableRepository.deleteAllByUserIdAndSubjectId(userId, subjectId);
        if (deleted == 0) {
            throw ApiException.notFound("시간표에서 해당 과목을 찾을 수 없습니다.");
        }
    }
    
    public List<UserTimetable> getUserTimetable(Long userId, String semester) {
        if (semester != null) {
            return userTimetableRepository.findByUserIdAndSemesterWithSubjectAndSchedules(userId, semester);
        } else {
            return userTimetableRepository.findByUserId(userId);
        }
    }
    
    @Transactional
    public UserTimetable updateMemo(Long userId, Long subjectId, String memo) {
        List<UserTimetable> items = userTimetableRepository.findAllByUserIdAndSubjectId(userId, subjectId);
        if (items.isEmpty()) {
            throw ApiException.notFound("시간표에서 해당 과목을 찾을 수 없습니다.");
        }
        items.forEach(item -> item.setMemo(memo));
        return items.get(0);
    }
    
    @Transactional
    public void removeAllSubjectsFromTimetable(Long userId, String semester) {
        List<UserTimetable> timetables;
        if (semester != null && !semester.isEmpty()) {
            timetables = userTimetableRepository.findByUserIdAndSemester(userId, semester);
        } else {
            timetables = userTimetableRepository.findByUserId(userId);
        }

        // 전체 비우기는 멱등 연산 — 이미 비어 있어도 오류가 아니라 0건 삭제로 정상 처리한다.
        if (!timetables.isEmpty()) {
            userTimetableRepository.deleteAll(timetables);
        }
    }
    
    private boolean hasTimeConflict(List<UserTimetable> currentTimetable, Subject newSubject) {
        for (UserTimetable existing : currentTimetable) {
            for (var existingSchedule : existing.getSubject().getSchedules()) {
                for (var newSchedule : newSubject.getSchedules()) {
                    // 온라인/시간 미지정 과목은 요일·시간이 null 이므로 충돌 판정에서 제외한다.
                    // (조합 서비스 SubjectOption.from 과 동일한 정책 — 두 경로의 동작을 일치시킨다.)
                    if (existingSchedule.getDayOfWeek() == null
                            || existingSchedule.getStartTime() == null
                            || existingSchedule.getEndTime() == null
                            || newSchedule.getDayOfWeek() == null
                            || newSchedule.getStartTime() == null
                            || newSchedule.getEndTime() == null) {
                        continue;
                    }
                    // 같은 요일인지 확인
                    if (existingSchedule.getDayOfWeek().equals(newSchedule.getDayOfWeek())) {
                        // 시간이 겹치는지 확인
                        double existingStart = existingSchedule.getStartTime();
                        double existingEnd = existingSchedule.getEndTime();
                        double newStart = newSchedule.getStartTime();
                        double newEnd = newSchedule.getEndTime();
                        
                        // 야간 과목 시간 조정 (startTime > endTime인 경우)
                        if (existingStart > existingEnd) {
                            existingEnd += 8.0;
                        }
                        if (newStart > newEnd) {
                            newEnd += 8.0;
                        }
                        
                        // 시간 겹침 확인: (시작시간 < 상대방 끝시간) && (끝시간 > 상대방 시작시간)
                        if (newStart < existingEnd && newEnd > existingStart) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
