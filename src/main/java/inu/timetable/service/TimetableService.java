package inu.timetable.service;

import inu.timetable.entity.Subject;
import inu.timetable.entity.User;
import inu.timetable.entity.UserTimetable;
import inu.timetable.repository.SubjectRepository;
import inu.timetable.repository.UserRepository;
import inu.timetable.repository.UserTimetableRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
    
    public UserTimetable addSubjectToTimetable(Long userId, Long subjectId, String semester, String memo) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
            
        Subject subject = subjectRepository.findById(subjectId)
            .orElseThrow(() -> new RuntimeException("과목을 찾을 수 없습니다."));
        
        // 이미 추가된 과목인지 확인
        Optional<UserTimetable> existing = userTimetableRepository.findByUserIdAndSubjectId(userId, subjectId);
        if (existing.isPresent()) {
            throw new RuntimeException("이미 시간표에 추가된 과목입니다.");
        }
        
        UserTimetable userTimetable = UserTimetable.builder()
            .user(user)
            .subject(subject)
            .semester(semester)
            .memo(memo)
            .build();
            
        return userTimetableRepository.save(userTimetable);
    }
    
    public void removeSubjectFromTimetable(Long userId, Long subjectId) {
        UserTimetable userTimetable = userTimetableRepository.findByUserIdAndSubjectId(userId, subjectId)
            .orElseThrow(() -> new RuntimeException("시간표에서 해당 과목을 찾을 수 없습니다."));
            
        userTimetableRepository.delete(userTimetable);
    }
    
    public List<UserTimetable> getUserTimetable(Long userId, String semester) {
        if (semester != null) {
            return userTimetableRepository.findByUserIdAndSemesterWithSubjectAndSchedules(userId, semester);
        } else {
            return userTimetableRepository.findByUserId(userId);
        }
    }
    
    public UserTimetable updateMemo(Long userId, Long subjectId, String memo) {
        UserTimetable userTimetable = userTimetableRepository.findByUserIdAndSubjectId(userId, subjectId)
            .orElseThrow(() -> new RuntimeException("시간표에서 해당 과목을 찾을 수 없습니다."));
            
        userTimetable.setMemo(memo);
        return userTimetableRepository.save(userTimetable);
    }
}