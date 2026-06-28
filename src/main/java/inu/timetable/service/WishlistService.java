package inu.timetable.service;

import inu.timetable.entity.Subject;
import inu.timetable.entity.User;
import inu.timetable.entity.WishlistItem;
import inu.timetable.exception.ApiException;
import inu.timetable.repository.SubjectRepository;
import inu.timetable.repository.UserRepository;
import inu.timetable.repository.WishlistRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
    
    @Transactional
    public WishlistItem addToWishlist(Long userId, Long subjectId, String semester, Integer priority) {
        return addToWishlist(userId, subjectId, semester, priority, false);
    }

    @Transactional
    public WishlistItem addToWishlist(Long userId, Long subjectId, String semester, Integer priority, Boolean isRequired) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> ApiException.notFound("사용자를 찾을 수 없습니다."));
            
        Subject subject = subjectRepository.findById(subjectId)
            .orElseThrow(() -> ApiException.notFound("과목을 찾을 수 없습니다."));
        
        // 같은 학기에 이미 담겨 있는지 확인(다른 학기에는 같은 과목을 담을 수 있다)
        if (wishlistRepository.existsByUserIdAndSubjectIdAndSemester(userId, subjectId, semester)) {
            throw ApiException.conflict("이미 위시리스트에 추가된 과목입니다.");
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
    public void removeFromWishlist(Long userId, Long subjectId) {
        wishlistRepository.deleteByUserIdAndSubjectId(userId, subjectId);
    }
    
    public List<WishlistItem> getUserWishlist(Long userId, String semester) {
        return wishlistRepository.findByUserIdAndSemesterWithSubjectAndSchedules(userId, semester);
    }
    
    @Transactional
    public WishlistItem updatePriority(Long userId, Long subjectId, Integer priority) {
        // 같은 과목이 여러 학기에 존재할 수 있으므로 단건 조회(NonUniqueResult 위험) 대신 모두 갱신한다.
        List<WishlistItem> items = wishlistRepository.findAllByUserIdAndSubjectId(userId, subjectId);
        if (items.isEmpty()) {
            throw ApiException.notFound("위시리스트에서 해당 과목을 찾을 수 없습니다.");
        }
        items.forEach(item -> item.setPriority(priority));
        return items.get(0);
    }

    @Transactional
    public WishlistItem updateRequired(Long userId, Long subjectId, Boolean isRequired) {
        List<WishlistItem> items = wishlistRepository.findAllByUserIdAndSubjectId(userId, subjectId);
        if (items.isEmpty()) {
            throw ApiException.notFound("위시리스트에서 해당 과목을 찾을 수 없습니다.");
        }
        items.forEach(item -> item.setIsRequired(isRequired));
        return items.get(0);
    }
    
}
