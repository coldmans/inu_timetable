package inu.timetable.service;

import inu.timetable.entity.Schedule;
import inu.timetable.entity.Subject;
import inu.timetable.entity.User;
import inu.timetable.entity.UserMajor;
import inu.timetable.entity.WishlistItem;
import inu.timetable.enums.UserMajorType;
import inu.timetable.repository.SubjectRepository;
import inu.timetable.repository.UserRepository;
import inu.timetable.repository.UserTimetableRepository;
import inu.timetable.repository.WishlistRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Profile({"dev", "local"})
public class DevSessionService {

    private static final String DEV_USERNAME = "codex-design-user";
    private static final String DEV_PASSWORD = "dev-session-only";
    private static final int TARGET_SEED_CREDITS = 18;
    private static final int MAX_SEED_SUBJECTS = 8;

    private final UserRepository userRepository;
    private final SubjectRepository subjectRepository;
    private final WishlistRepository wishlistRepository;
    private final UserTimetableRepository userTimetableRepository;
    private final PasswordEncoder passwordEncoder;

    public DevSessionService(
            UserRepository userRepository,
            SubjectRepository subjectRepository,
            WishlistRepository wishlistRepository,
            UserTimetableRepository userTimetableRepository,
            @Qualifier("userPasswordEncoder") PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.subjectRepository = subjectRepository;
        this.wishlistRepository = wishlistRepository;
        this.userTimetableRepository = userTimetableRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public DevSessionResult prepareSession(String semester, boolean seedWishlist, boolean reset) {
        User user = userRepository.findByUsername(DEV_USERNAME)
                .orElseGet(this::createDevUser);

        if (reset) {
            userTimetableRepository.deleteAll(userTimetableRepository.findByUserIdAndSemester(user.getId(), semester));
            wishlistRepository.deleteAll(wishlistRepository.findByUserIdAndSemester(user.getId(), semester));
        }

        int seededWishlistCount = 0;
        if (seedWishlist && wishlistRepository.findByUserIdAndSemester(user.getId(), semester).isEmpty()) {
            seededWishlistCount = seedWishlist(user, semester);
        }

        int wishlistCount = wishlistRepository.findByUserIdAndSemester(user.getId(), semester).size();
        int timetableCount = userTimetableRepository.findByUserIdAndSemester(user.getId(), semester).size();
        return new DevSessionResult(user, semester, wishlistCount, timetableCount, seededWishlistCount);
    }

    private User createDevUser() {
        User user = User.builder()
                .username(DEV_USERNAME)
                .password(passwordEncoder.encode(DEV_PASSWORD))
                .nickname("디자인 테스트")
                .grade(3)
                .major("컴퓨터공학부")
                .build();
        user.addUserMajor(UserMajor.builder()
                .type(UserMajorType.PRIMARY)
                .department("컴퓨터공학부")
                .build());
        return userRepository.save(user);
    }

    private int seedWishlist(User user, String semester) {
        List<Subject> selectedSubjects = selectSeedSubjects(semester);
        int priority = 1;
        for (Subject subject : selectedSubjects) {
            WishlistItem item = WishlistItem.builder()
                    .user(user)
                    .subject(subject)
                    .semester(semester)
                    .priority(priority++)
                    .isRequired(false)
                    .build();
            wishlistRepository.save(item);
        }
        return selectedSubjects.size();
    }

    private List<Subject> selectSeedSubjects(String semester) {
        List<Subject> candidates = subjectRepository
                .findActiveSeedCandidatesBySemester(semester, PageRequest.of(0, 200, Sort.by("id").ascending()))
                .getContent();
        List<Subject> selected = new ArrayList<>();
        int totalCredits = 0;

        for (Subject candidate : candidates) {
            if (!isSeedCandidate(candidate) || hasConflictWithSelected(selected, candidate)) {
                continue;
            }

            selected.add(candidate);
            totalCredits += candidate.getCredits();
            if (selected.size() >= MAX_SEED_SUBJECTS || totalCredits >= TARGET_SEED_CREDITS) {
                break;
            }
        }

        if (selected.isEmpty()) {
            candidates.stream()
                    .filter(this::isSeedCandidate)
                    .limit(MAX_SEED_SUBJECTS)
                    .forEach(selected::add);
        }

        return selected;
    }

    private boolean isSeedCandidate(Subject subject) {
        return Boolean.TRUE.equals(subject.getActive())
                && subject.getCredits() != null
                && subject.getCredits() > 0
                && subject.getSubjectName() != null
                && subject.getSubjectType() != null
                && subject.getClassMethod() != null;
    }

    private boolean hasConflictWithSelected(List<Subject> selectedSubjects, Subject candidate) {
        return selectedSubjects.stream().anyMatch(selected -> hasTimeConflict(selected, candidate));
    }

    private boolean hasTimeConflict(Subject first, Subject second) {
        for (Schedule firstSchedule : first.getSchedules()) {
            for (Schedule secondSchedule : second.getSchedules()) {
                if (isScheduleConflict(firstSchedule, secondSchedule)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isScheduleConflict(Schedule first, Schedule second) {
        if (first.getDayOfWeek() == null || second.getDayOfWeek() == null) {
            return false;
        }
        if (!first.getDayOfWeek().equals(second.getDayOfWeek())) {
            return false;
        }
        if (first.getStartTime() == null || first.getEndTime() == null
                || second.getStartTime() == null || second.getEndTime() == null) {
            return false;
        }

        return first.getStartTime() < second.getEndTime()
                && second.getStartTime() < first.getEndTime();
    }

    public record DevSessionResult(
            User user,
            String semester,
            int wishlistCount,
            int timetableCount,
            int seededWishlistCount) {
    }
}
