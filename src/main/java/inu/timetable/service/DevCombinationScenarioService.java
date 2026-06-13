package inu.timetable.service;

import inu.timetable.entity.Schedule;
import inu.timetable.entity.Subject;
import inu.timetable.entity.User;
import inu.timetable.entity.UserMajor;
import inu.timetable.entity.WishlistItem;
import inu.timetable.enums.ClassMethod;
import inu.timetable.enums.SubjectType;
import inu.timetable.enums.UserMajorType;
import inu.timetable.repository.SubjectRepository;
import inu.timetable.repository.UserRepository;
import inu.timetable.repository.UserTimetableRepository;
import inu.timetable.repository.WishlistRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@Profile({"dev", "local"})
public class DevCombinationScenarioService {

    private static final String DEFAULT_USERNAME = "codex-combination-k6";
    private static final String DEFAULT_SEMESTER = "2026-1";
    private static final String PASSWORD = "dev-session-only";
    private static final int MIN_WISHLIST_SIZE = 6;
    private static final int MAX_WISHLIST_SIZE = 36;
    private static final int MIN_SLOT_COUNT = 3;
    private static final int MAX_SLOT_COUNT = 10;
    private static final List<String> DAYS = List.of("월", "화", "수", "목", "금");

    private final UserRepository userRepository;
    private final SubjectRepository subjectRepository;
    private final WishlistRepository wishlistRepository;
    private final UserTimetableRepository userTimetableRepository;
    private final PasswordEncoder passwordEncoder;

    public DevCombinationScenarioService(
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
    public ScenarioResult prepareScenario(ScenarioRequest request) {
        String semester = StringUtils.hasText(request.semester()) ? request.semester().trim() : DEFAULT_SEMESTER;
        String username = StringUtils.hasText(request.username()) ? request.username().trim() : DEFAULT_USERNAME;
        int wishlistSize = clamp(request.wishlistSize(), MIN_WISHLIST_SIZE, MAX_WISHLIST_SIZE, 24);
        int slotCount = clamp(request.slotCount(), MIN_SLOT_COUNT, MAX_SLOT_COUNT, 6);
        boolean reset = Boolean.TRUE.equals(request.reset());

        User user = userRepository.findByUsername(username)
                .orElseGet(() -> createUser(username));
        if (reset) {
            userTimetableRepository.deleteAll(userTimetableRepository.findByUserIdAndSemester(user.getId(), semester));
            wishlistRepository.deleteAll(wishlistRepository.findByUserIdAndSemester(user.getId(), semester));
            wishlistRepository.flush();
        }

        List<Subject> subjects = prepareSubjects(semester, wishlistSize, slotCount);
        List<WishlistItem> currentWishlist = wishlistRepository.findByUserIdAndSemester(user.getId(), semester);
        if (currentWishlist.isEmpty()) {
            seedWishlist(user, semester, subjects);
        }

        int wishlistCount = wishlistRepository.findByUserIdAndSemester(user.getId(), semester).size();
        return new ScenarioResult(user, semester, wishlistSize, slotCount, wishlistCount);
    }

    private User createUser(String username) {
        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(PASSWORD))
                .nickname("조합 성능 테스트")
                .grade(3)
                .major("컴퓨터공학부")
                .build();
        user.addUserMajor(UserMajor.builder()
                .type(UserMajorType.PRIMARY)
                .department("컴퓨터공학부")
                .build());
        return userRepository.save(user);
    }

    private List<Subject> prepareSubjects(String semester, int wishlistSize, int slotCount) {
        List<Subject> subjects = new ArrayList<>();
        int groupSize = (int) Math.ceil((double) wishlistSize / slotCount);

        for (int index = 0; index < wishlistSize; index++) {
            int slot = Math.min(index / groupSize, slotCount - 1);
            subjects.add(prepareSubject(semester, wishlistSize, slotCount, index, slot));
        }

        return subjects;
    }

    private Subject prepareSubject(String semester, int wishlistSize, int slotCount, int index, int slot) {
        String courseCode = "PERF-COMB-%02d-%02d-%03d".formatted(wishlistSize, slotCount, index + 1);
        Subject subject = subjectRepository.findFirstByCourseCodeAndSemesterOrderByIdAsc(courseCode, semester)
                .orElseGet(() -> Subject.builder()
                        .courseCode(courseCode)
                        .semester(semester)
                        .schedules(new ArrayList<>())
                        .build());

        subject.setActive(true);
        subject.setSubjectName("조합성능테스트%02d-%02d".formatted(slot + 1, index + 1));
        subject.setCredits(3);
        subject.setProfessor("성능교수%02d".formatted(slot + 1));
        subject.setDepartment("컴퓨터공학부");
        subject.setGrade(3);
        subject.setSubjectType(SubjectType.전심);
        subject.setClassMethod(ClassMethod.OFFLINE);
        subject.setIsNight(false);
        subject.getSchedules().clear();
        subject.getSchedules().add(schedule(subject, slot));
        return subjectRepository.save(subject);
    }

    private Schedule schedule(Subject subject, int slot) {
        String day = DAYS.get(slot % DAYS.size());
        double startTime = 1.0 + (slot / DAYS.size()) * 3.0;
        return Schedule.builder()
                .subject(subject)
                .dayOfWeek(day)
                .startTime(startTime)
                .endTime(startTime + 1.5)
                .build();
    }

    private void seedWishlist(User user, String semester, List<Subject> subjects) {
        int priority = 1;
        for (Subject subject : subjects) {
            wishlistRepository.save(WishlistItem.builder()
                    .user(user)
                    .subject(subject)
                    .semester(semester)
                    .priority(priority++)
                    .isRequired(false)
                    .build());
        }
    }

    private int clamp(Integer value, int min, int max, int fallback) {
        int resolved = value == null ? fallback : value;
        return Math.max(min, Math.min(max, resolved));
    }

    public record ScenarioRequest(
            String username,
            String semester,
            Integer wishlistSize,
            Integer slotCount,
            Boolean reset) {
    }

    public record ScenarioResult(
            User user,
            String semester,
            int requestedWishlistSize,
            int slotCount,
            int wishlistCount) {
    }
}
