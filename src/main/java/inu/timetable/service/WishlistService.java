package inu.timetable.service;

import inu.timetable.entity.Subject;
import inu.timetable.entity.User;
import inu.timetable.entity.WishlistItem;
import inu.timetable.repository.SubjectRepository;
import inu.timetable.repository.UserRepository;
import inu.timetable.repository.WishlistRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class WishlistService {
    
    private final WishlistRepository wishlistRepository;
    private final UserRepository userRepository;
    private final SubjectRepository subjectRepository;
    
    @Autowired
    public WishlistService(WishlistRepository wishlistRepository,
                          UserRepository userRepository,
                          SubjectRepository subjectRepository) {
        this.wishlistRepository = wishlistRepository;
        this.userRepository = userRepository;
        this.subjectRepository = subjectRepository;
    }
    
    public WishlistItem addToWishlist(Long userId, Long subjectId, String semester, Integer priority) {
        return addToWishlist(userId, subjectId, semester, priority, false);
    }
    
    public WishlistItem addToWishlist(Long userId, Long subjectId, String semester, Integer priority, Boolean isRequired) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
            
        Subject subject = subjectRepository.findById(subjectId)
            .orElseThrow(() -> new RuntimeException("과목을 찾을 수 없습니다."));
        
        // 이미 위시리스트에 있는지 확인
        Optional<WishlistItem> existing = wishlistRepository.findByUserIdAndSubjectIdAndSemester(userId, subjectId, semester);
        if (existing.isPresent()) {
            throw new RuntimeException("이미 위시리스트에 추가된 과목입니다.");
        }
        
        WishlistItem wishlistItem = WishlistItem.builder()
            .user(user)
            .subject(subject)
            .semester(semester)
            .priority(priority != null ? priority : 3) // 기본 우선순위: 중간
            .isRequired(isRequired != null ? isRequired : false)
            .build();
            
        return wishlistRepository.save(wishlistItem);
    }
    
    @Transactional
    public void removeFromWishlist(Long userId, Long subjectId, String semester) {
        int deleted = wishlistRepository.deleteByUserIdAndSubjectIdAndSemester(userId, subjectId, semester);
        if (deleted == 0) {
            throw new RuntimeException("위시리스트에서 해당 과목을 찾을 수 없습니다.");
        }
    }
    
    public List<WishlistItem> getUserWishlist(Long userId, String semester) {
        return wishlistRepository.findByUserIdAndSemesterWithSubjectAndSchedules(userId, semester);
    }
    
    public WishlistItem updatePriority(Long userId, Long subjectId, String semester, Integer priority) {
        WishlistItem wishlistItem = wishlistRepository.findByUserIdAndSubjectIdAndSemester(userId, subjectId, semester)
            .orElseThrow(() -> new RuntimeException("위시리스트에서 해당 과목을 찾을 수 없습니다."));
            
        wishlistItem.setPriority(priority);
        return wishlistRepository.save(wishlistItem);
    }
    
    public WishlistItem updateRequired(Long userId, Long subjectId, String semester, Boolean isRequired) {
        WishlistItem wishlistItem = wishlistRepository.findByUserIdAndSubjectIdAndSemester(userId, subjectId, semester)
            .orElseThrow(() -> new RuntimeException("위시리스트에서 해당 과목을 찾을 수 없습니다."));
            
        wishlistItem.setIsRequired(isRequired);
        return wishlistRepository.save(wishlistItem);
    }
    
    public List<WishlistItem> getAllWishlistItems(Long userId) {
        return wishlistRepository.findAll().stream()
            .filter(item -> item.getUser().getId().equals(userId))
            .collect(java.util.stream.Collectors.toList());
    }
}