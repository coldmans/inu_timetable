package inu.timetable.service;

import inu.timetable.entity.Subject;
import inu.timetable.entity.User;
import inu.timetable.entity.UserTimetable;
import inu.timetable.repository.SubjectRepository;
import inu.timetable.repository.UserRepository;
import inu.timetable.repository.UserTimetableRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

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
            .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
            
        Subject subject = subjectRepository.findById(subjectId)
            .orElseThrow(() -> new RuntimeException("과목을 찾을 수 없습니다."));
        
        // 이미 추가된 과목인지 확인
        Optional<UserTimetable> existing = userTimetableRepository.findByUserIdAndSubjectIdAndSemester(userId, subjectId, semester);
        if (existing.isPresent()) {
            throw new RuntimeException("이미 시간표에 추가된 과목입니다.");
        }
        
        // 시간표 겹침 확인
        List<UserTimetable> currentTimetable = userTimetableRepository.findByUserIdAndSemesterWithSubjectAndSchedules(userId, semester);
        if (hasTimeConflict(currentTimetable, subject)) {
            throw new RuntimeException("시간표가 겹치는 과목이 있습니다.");
        }
        
        UserTimetable userTimetable = UserTimetable.builder()
            .user(user)
            .subject(subject)
            .semester(semester)
            .memo(memo)
            .build();

        try {
            return userTimetableRepository.save(userTimetable);
        } catch (DataIntegrityViolationException e) {
            throw new RuntimeException("이미 시간표에 추가된 과목입니다.");
        }
    }
    
    @Transactional
    public void removeSubjectFromTimetable(Long userId, Long subjectId, String semester) {
        int deleted = userTimetableRepository.deleteByUserIdAndSubjectIdAndSemester(userId, subjectId, semester);
        if (deleted == 0) {
            throw new RuntimeException("시간표에서 해당 과목을 찾을 수 없습니다.");
        }
    }
    
    public List<UserTimetable> getUserTimetable(Long userId, String semester) {
        if (semester != null) {
            return userTimetableRepository.findByUserIdAndSemesterWithSubjectAndSchedules(userId, semester);
        } else {
            return userTimetableRepository.findByUserId(userId);
        }
    }
    
    public UserTimetable updateMemo(Long userId, Long subjectId, String semester, String memo) {
        UserTimetable userTimetable;
        if (semester != null && !semester.isBlank()) {
            userTimetable = userTimetableRepository.findByUserIdAndSubjectIdAndSemester(userId, subjectId, semester)
                .orElseThrow(() -> new RuntimeException("시간표에서 해당 과목을 찾을 수 없습니다."));
        } else {
            userTimetable = userTimetableRepository.findByUserIdAndSubjectId(userId, subjectId).stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("시간표에서 해당 과목을 찾을 수 없습니다."));
        }

        userTimetable.setMemo(memo);
        return userTimetableRepository.save(userTimetable);
    }
    
    public void removeAllSubjectsFromTimetable(Long userId, String semester) {
        List<UserTimetable> timetables;
        if (semester != null && !semester.isEmpty()) {
            timetables = userTimetableRepository.findByUserIdAndSemester(userId, semester);
        } else {
            timetables = userTimetableRepository.findByUserId(userId);
        }
        
        if (timetables.isEmpty()) {
            throw new RuntimeException("삭제할 시간표가 없습니다.");
        }
        
        userTimetableRepository.deleteAll(timetables);
    }
    
    private boolean hasTimeConflict(List<UserTimetable> currentTimetable, Subject newSubject) {
        for (UserTimetable existing : currentTimetable) {
            for (var existingSchedule : existing.getSubject().getSchedules()) {
                for (var newSchedule : newSubject.getSchedules()) {
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