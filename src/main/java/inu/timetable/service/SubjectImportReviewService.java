package inu.timetable.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import inu.timetable.dto.SubjectImportApplyResponse;
import inu.timetable.dto.SubjectImportImpactResponse;
import inu.timetable.dto.SubjectImportPlanResponse;
import inu.timetable.dto.TimetableReconciliationResult;
import inu.timetable.entity.Schedule;
import inu.timetable.entity.Subject;
import inu.timetable.entity.SubjectImportPlan;
import inu.timetable.entity.UserTimetable;
import inu.timetable.enums.ClassMethod;
import inu.timetable.enums.SubjectImportChangeCategory;
import inu.timetable.enums.SubjectType;
import inu.timetable.event.SubjectDataChangedEvent;
import inu.timetable.repository.SubjectImportPlanRepository;
import inu.timetable.repository.SubjectRepository;
import inu.timetable.repository.UserTimetableRepository;
import inu.timetable.repository.WishlistRepository;
import inu.timetable.util.BusinessTime;
import inu.timetable.util.TimeConverter;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubjectImportReviewService {

    private static final String STATUS_PREVIEWED = "PREVIEWED";
    private static final String STATUS_APPLIED = "APPLIED";
    private static final int MAX_CONFLICT_DETAILS = 100;
    private static final int CURRENT_COMPARISON_VERSION = 2;
    private static final String DAYS = "월화수목금토일";

    private final OfficialSubjectImportService officialSubjectImportService;
    private final SubjectRepository subjectRepository;
    private final SubjectImportPlanRepository planRepository;
    private final UserTimetableRepository userTimetableRepository;
    private final WishlistRepository wishlistRepository;
    private final TimetableConflictResolutionService timetableConflictResolutionService;
    private final SubjectUpdateLogService subjectUpdateLogService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;
    private final Clock clock;

    @Transactional
    public SubjectImportPlanResponse preview(
            MultipartFile file,
            String semester,
            boolean deactivateMissing) throws IOException {
        String normalizedSemester = normalizeSemester(semester);
        byte[] sourceBytes = file == null ? new byte[0] : file.getBytes();
        OfficialSubjectImportService.ParsedWorkbook parsed =
                officialSubjectImportService.parseOfficialFile(file, normalizedSemester);
        List<Subject> candidates = subjectRepository.findImportCandidatesBySemester(normalizedSemester);
        List<SubjectImportPlanResponse.ChangeItem> changes =
                createChanges(parsed.records(), candidates, deactivateMissing);
        int unchangedCount = parsed.records().size()
                - (int) changes.stream()
                .filter(change -> !change.categories().contains(SubjectImportChangeCategory.CANCELED))
                .count();
        List<String> warnings = warnings(parsed.records(), changes);
        StoredPlan stored = new StoredPlan(CURRENT_COMPARISON_VERSION, changes, warnings);
        LocalDateTime now = nowInKorea();
        SubjectImportPlan plan = SubjectImportPlan.builder()
                .id(UUID.randomUUID().toString())
                .semester(normalizedSemester)
                .sourceFormat(parsed.sourceFormat())
                .sourceFilename(safeFilename(file == null ? null : file.getOriginalFilename()))
                .sourceSha256(sha256(sourceBytes))
                .status(STATUS_PREVIEWED)
                .deactivateMissing(deactivateMissing)
                .totalRows(parsed.records().size())
                .unchangedCount(Math.max(unchangedCount, 0))
                .planPayload(write(stored))
                .createdAt(now)
                .build();
        planRepository.save(plan);

        List<String> allCourseCodes = changes.stream()
                .map(SubjectImportPlanResponse.ChangeItem::courseCode)
                .toList();
        return toResponse(plan, stored, allCourseCodes);
    }

    @Transactional(readOnly = true)
    public SubjectImportPlanResponse get(String planId) {
        SubjectImportPlan plan = findPlan(planId);
        StoredPlan stored = read(plan.getPlanPayload(), StoredPlan.class);
        List<String> courseCodes = stored.changes().stream()
                .map(SubjectImportPlanResponse.ChangeItem::courseCode)
                .toList();
        return toResponse(plan, stored, courseCodes);
    }

    @Transactional(readOnly = true)
    public SubjectImportImpactResponse impact(String planId, List<String> courseCodes) {
        SubjectImportPlan plan = requirePreviewedPlan(planId);
        StoredPlan stored = read(plan.getPlanPayload(), StoredPlan.class);
        List<SubjectImportPlanResponse.ChangeItem> selected =
                selectedChanges(stored, courseCodes);
        return calculateImpact(plan.getSemester(), selected);
    }

    @Transactional
    public SubjectImportApplyResponse apply(String planId, List<String> courseCodes) {
        SubjectImportPlan plan = requirePreviewedPlanForApply(planId);
        StoredPlan stored = read(plan.getPlanPayload(), StoredPlan.class);
        requireCurrentComparisonVersion(stored);
        List<SubjectImportPlanResponse.ChangeItem> selected =
                selectedChanges(stored, courseCodes);
        SubjectImportImpactResponse previewedImpact =
                calculateImpact(plan.getSemester(), selected);

        Map<String, Subject> currentByCode = indexSubjectsByCourseCode(
                subjectRepository.findImportCandidatesBySemester(plan.getSemester()));
        List<String> staleCodes = selected.stream()
                .filter(change -> isStale(change, currentByCode.get(change.courseCode())))
                .map(SubjectImportPlanResponse.ChangeItem::courseCode)
                .toList();
        if (!staleCodes.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "미리보기 이후 DB가 변경된 과목이 있습니다. 다시 검토해주세요: "
                            + String.join(", ", staleCodes));
        }

        List<Subject> subjectsToSave = new ArrayList<>();
        List<Subject> modifiedSubjects = new ArrayList<>();
        List<Subject> timeChangedSubjects = new ArrayList<>();
        List<Subject> canceledSubjects = new ArrayList<>();
        int addedCount = 0;
        int modifiedCount = 0;
        int canceledCount = 0;

        for (SubjectImportPlanResponse.ChangeItem change : selected) {
            Subject subject = currentByCode.get(change.courseCode());
            if (change.categories().contains(SubjectImportChangeCategory.ADDED)) {
                subject = Subject.builder().schedules(new ArrayList<>()).build();
                addedCount++;
            } else if (change.categories().contains(SubjectImportChangeCategory.CANCELED)) {
                subject.setActive(false);
                canceledSubjects.add(subject);
                canceledCount++;
            } else {
                modifiedSubjects.add(subject);
                modifiedCount++;
            }

            if (!change.categories().contains(SubjectImportChangeCategory.CANCELED)) {
                officialSubjectImportService.applyRecord(subject, toRecord(change.after()));
            }
            if (change.categories().contains(SubjectImportChangeCategory.TIME_CHANGED)) {
                timeChangedSubjects.add(subject);
            }
            subjectsToSave.add(subject);
        }

        subjectRepository.saveAll(subjectsToSave);
        subjectRepository.flush();
        TimetableReconciliationResult reconciliation =
                timetableConflictResolutionService.reconcileImportChanges(
                        modifiedSubjects,
                        timeChangedSubjects,
                        canceledSubjects,
                        plan.getSemester());

        subjectUpdateLogService.record(
                plan.getSemester(),
                plan.getSourceFormat() + "_REVIEW",
                addedCount,
                modifiedCount,
                canceledCount);
        eventPublisher.publishEvent(new SubjectDataChangedEvent("reviewed-subject-import"));

        entityManager.flush();
        entityManager.clear();
        List<SubjectImportApplyResponse.VerificationItem> verification =
                verifyApplied(selected, plan.getSemester());
        boolean verified = verification.stream()
                .allMatch(SubjectImportApplyResponse.VerificationItem::matched);
        if (!verified) {
            throw new IllegalStateException("선택 반영 후 DB 재검증에 실패했습니다.");
        }

        LocalDateTime appliedAt = nowInKorea();
        SubjectImportApplyResponse response = new SubjectImportApplyResponse(
                plan.getId(),
                true,
                true,
                appliedAt,
                selected.size(),
                addedCount,
                modifiedCount,
                canceledCount,
                previewedImpact,
                reconciliation,
                verification);
        SubjectImportPlan managedPlan = findPlan(planId);
        managedPlan.setStatus(STATUS_APPLIED);
        managedPlan.setAppliedAt(appliedAt);
        managedPlan.setApplyResult(write(response));
        return response;
    }

    private SubjectImportPlanResponse toResponse(
            SubjectImportPlan plan,
            StoredPlan stored,
            List<String> selectedCourseCodes) {
        List<SubjectImportPlanResponse.ChangeItem> changes =
                withCourseImpact(plan.getSemester(), stored.changes());
        Map<String, SubjectImportPlanResponse.ChangeItem> byCode = changes.stream()
                .collect(Collectors.toMap(
                        SubjectImportPlanResponse.ChangeItem::courseCode,
                        Function.identity()));
        List<SubjectImportPlanResponse.ChangeItem> selected = selectedCourseCodes.stream()
                .map(byCode::get)
                .filter(Objects::nonNull)
                .toList();
        return new SubjectImportPlanResponse(
                plan.getId(),
                plan.getStatus(),
                plan.getSemester(),
                plan.getSourceFormat(),
                plan.getSourceFilename(),
                plan.getTotalRows(),
                changes.size(),
                plan.getUnchangedCount(),
                Boolean.TRUE.equals(plan.getDeactivateMissing()),
                plan.getCreatedAt(),
                changes,
                calculateImpact(plan.getSemester(), selected),
                stored.warnings());
    }

    private List<SubjectImportPlanResponse.ChangeItem> createChanges(
            List<OfficialSubjectImportService.OfficialSubjectRecord> records,
            List<Subject> candidates,
            boolean deactivateMissing) {
        Map<String, Subject> existingByCode = indexSubjectsByCourseCode(candidates);
        List<SubjectImportPlanResponse.ChangeItem> changes = new ArrayList<>();
        Set<String> incomingCodes = new LinkedHashSet<>();

        for (OfficialSubjectImportService.OfficialSubjectRecord record : records) {
            incomingCodes.add(record.courseCode());
            Subject existing = existingByCode.get(record.courseCode());
            SubjectImportPlanResponse.SubjectSnapshot after = snapshot(record);
            if (existing == null) {
                changes.add(new SubjectImportPlanResponse.ChangeItem(
                        record.courseCode(),
                        record.subjectName(),
                        EnumSet.of(SubjectImportChangeCategory.ADDED),
                        List.of(new SubjectImportPlanResponse.FieldChange("개설 상태", "없음", "추가")),
                        null,
                        after,
                        new SubjectImportPlanResponse.CourseImpact(0, 0)));
                continue;
            }

            SubjectImportPlanResponse.SubjectSnapshot before = snapshot(existing);
            List<SubjectImportPlanResponse.FieldChange> fields = fieldChanges(before, after);
            if (fields.isEmpty()) {
                continue;
            }
            changes.add(new SubjectImportPlanResponse.ChangeItem(
                    record.courseCode(),
                    record.subjectName(),
                    categories(before, after, fields),
                    fields,
                    before,
                    after,
                    new SubjectImportPlanResponse.CourseImpact(0, 0)));
        }

        if (deactivateMissing) {
            candidates.stream()
                    .filter(subject -> Boolean.TRUE.equals(subject.getActive()))
                    .filter(subject -> hasText(subject.getCourseCode()))
                    .filter(subject -> !incomingCodes.contains(subject.getCourseCode()))
                    .forEach(subject -> {
                        SubjectImportPlanResponse.SubjectSnapshot before = snapshot(subject);
                        SubjectImportPlanResponse.SubjectSnapshot after =
                                withActive(before, false);
                        changes.add(new SubjectImportPlanResponse.ChangeItem(
                                subject.getCourseCode(),
                                subject.getSubjectName(),
                                EnumSet.of(SubjectImportChangeCategory.CANCELED),
                                List.of(new SubjectImportPlanResponse.FieldChange(
                                        "개설 상태", "개설", "폐강")),
                                before,
                                after,
                                new SubjectImportPlanResponse.CourseImpact(0, 0)));
                    });
        }

        return changes;
    }

    private List<SubjectImportPlanResponse.FieldChange> fieldChanges(
            SubjectImportPlanResponse.SubjectSnapshot before,
            SubjectImportPlanResponse.SubjectSnapshot after) {
        List<SubjectImportPlanResponse.FieldChange> fields = new ArrayList<>();
        addField(fields, "교과목명", before.subjectName(), after.subjectName());
        addField(fields, "학점", before.credits(), after.credits());
        addField(fields, "담당교수", before.professor(), after.professor());
        addField(fields, "학과", before.department(), after.department());
        addField(fields, "학년", before.grade(), after.grade());
        addField(fields, "이수구분", before.subjectType(), after.subjectType());
        addField(fields, "수업유형", before.classMethod(), after.classMethod());
        addField(fields, "야간여부", before.night(), after.night());
        if (!scheduleKeys(before.schedules()).equals(scheduleKeys(after.schedules()))) {
            addField(fields, "시간표", scheduleText(before.schedules()), scheduleText(after.schedules()));
        }
        if (!roomKeys(before.schedules()).equals(roomKeys(after.schedules()))) {
            addField(fields, "강의실", roomText(before.schedules()), roomText(after.schedules()));
        }
        if (before.active() != after.active()) {
            addField(fields, "개설 상태", before.active() ? "개설" : "비활성", after.active() ? "개설" : "비활성");
        }
        return fields;
    }

    private Set<SubjectImportChangeCategory> categories(
            SubjectImportPlanResponse.SubjectSnapshot before,
            SubjectImportPlanResponse.SubjectSnapshot after,
            List<SubjectImportPlanResponse.FieldChange> fields) {
        EnumSet<SubjectImportChangeCategory> categories =
                EnumSet.noneOf(SubjectImportChangeCategory.class);
        Set<String> labels = fields.stream()
                .map(SubjectImportPlanResponse.FieldChange::field)
                .collect(Collectors.toSet());
        if (labels.contains("시간표")) {
            categories.add(SubjectImportChangeCategory.TIME_CHANGED);
        }
        if (labels.contains("강의실")) {
            categories.add(SubjectImportChangeCategory.ROOM_CHANGED);
        }
        if (!before.active() && after.active()) {
            categories.add(SubjectImportChangeCategory.REACTIVATED);
        }
        Set<String> categorizedLabels = Set.of("시간표", "강의실", "개설 상태");
        if (fields.stream().anyMatch(field -> !categorizedLabels.contains(field.field()))) {
            categories.add(SubjectImportChangeCategory.DETAILS_CHANGED);
        }
        return categories;
    }

    private List<SubjectImportPlanResponse.ChangeItem> withCourseImpact(
            String semester,
            List<SubjectImportPlanResponse.ChangeItem> changes) {
        List<Long> subjectIds = changes.stream()
                .map(SubjectImportPlanResponse.ChangeItem::before)
                .filter(Objects::nonNull)
                .map(SubjectImportPlanResponse.SubjectSnapshot::id)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (subjectIds.isEmpty()) {
            return changes;
        }
        Map<Long, Long> timetableCounts = userTimetableRepository
                .countAddedUsersBySubjectIdsAndSemester(subjectIds, semester)
                .stream()
                .collect(Collectors.toMap(
                        UserTimetableRepository.SubjectTimetableAddCount::getSubjectId,
                        UserTimetableRepository.SubjectTimetableAddCount::getTimetableAddCount));
        Map<Long, Long> wishlistCounts = wishlistRepository
                .countBySubjectIdsAndSemester(subjectIds, semester)
                .stream()
                .collect(Collectors.toMap(
                        WishlistRepository.SubjectWishlistCount::getSubjectId,
                        WishlistRepository.SubjectWishlistCount::getWishlistCount));
        return changes.stream()
                .map(change -> {
                    Long subjectId = change.before() == null ? null : change.before().id();
                    return new SubjectImportPlanResponse.ChangeItem(
                            change.courseCode(),
                            change.subjectName(),
                            change.categories(),
                            change.fields(),
                            change.before(),
                            change.after(),
                            new SubjectImportPlanResponse.CourseImpact(
                                    Math.toIntExact(timetableCounts.getOrDefault(subjectId, 0L)),
                                    Math.toIntExact(wishlistCounts.getOrDefault(subjectId, 0L))));
                })
                .toList();
    }

    private SubjectImportImpactResponse calculateImpact(
            String semester,
            List<SubjectImportPlanResponse.ChangeItem> selected) {
        List<Long> affectedIds = selected.stream()
                .map(SubjectImportPlanResponse.ChangeItem::before)
                .filter(Objects::nonNull)
                .map(SubjectImportPlanResponse.SubjectSnapshot::id)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Set<Long> timetableUsers = affectedIds.isEmpty()
                ? Set.of()
                : new LinkedHashSet<>(userTimetableRepository
                .findDistinctUserIdsBySubjectIdsAndSemester(affectedIds, semester));
        Set<Long> wishlistUsers = affectedIds.isEmpty()
                ? Set.of()
                : new LinkedHashSet<>(wishlistRepository
                .findDistinctUserIdsBySubjectIdsAndSemester(affectedIds, semester));
        Set<Long> totalUsers = new LinkedHashSet<>(timetableUsers);
        totalUsers.addAll(wishlistUsers);

        Set<Long> canceledIds = selected.stream()
                .filter(change -> change.categories().contains(SubjectImportChangeCategory.CANCELED))
                .map(SubjectImportPlanResponse.ChangeItem::before)
                .filter(Objects::nonNull)
                .map(SubjectImportPlanResponse.SubjectSnapshot::id)
                .collect(Collectors.toSet());
        int cancellationEntries = canceledIds.isEmpty()
                ? 0
                : userTimetableRepository.findAllBySubjectIdsAndSemesterWithUserAndSubject(
                new ArrayList<>(canceledIds), semester).size();

        Map<Long, SubjectImportPlanResponse.ChangeItem> timeChanges = selected.stream()
                .filter(change -> change.categories().contains(SubjectImportChangeCategory.TIME_CHANGED))
                .filter(change -> change.before() != null)
                .collect(Collectors.toMap(change -> change.before().id(), Function.identity()));
        List<SubjectImportImpactResponse.ProjectedConflict> conflicts =
                projectedConflicts(semester, timeChanges, canceledIds);
        Set<Long> conflictUsers = conflicts.stream()
                .map(SubjectImportImpactResponse.ProjectedConflict::userId)
                .collect(Collectors.toSet());
        boolean truncated = conflicts.size() > MAX_CONFLICT_DETAILS;
        List<SubjectImportImpactResponse.ProjectedConflict> visibleConflicts =
                truncated ? conflicts.subList(0, MAX_CONFLICT_DETAILS) : conflicts;

        return new SubjectImportImpactResponse(
                selected.size(),
                timetableUsers.size(),
                wishlistUsers.size(),
                totalUsers.size(),
                conflictUsers.size(),
                conflicts.size(),
                cancellationEntries,
                visibleConflicts,
                truncated);
    }

    private List<SubjectImportImpactResponse.ProjectedConflict> projectedConflicts(
            String semester,
            Map<Long, SubjectImportPlanResponse.ChangeItem> timeChanges,
            Set<Long> canceledIds) {
        if (timeChanges.isEmpty()) {
            return List.of();
        }
        List<Long> candidateUserIds = userTimetableRepository
                .findDistinctUserIdsBySubjectIdsAndSemester(
                        new ArrayList<>(timeChanges.keySet()),
                        semester);
        if (candidateUserIds.isEmpty()) {
            return List.of();
        }
        Map<Long, List<UserTimetable>> timetablesByUser = new LinkedHashMap<>();
        userTimetableRepository
                .findAllByUserIdsAndSemesterWithSubjectAndSchedules(
                        candidateUserIds,
                        semester)
                .forEach(entry -> timetablesByUser
                        .computeIfAbsent(entry.getUser().getId(), ignored -> new ArrayList<>())
                        .add(entry));
        List<SubjectImportImpactResponse.ProjectedConflict> conflicts = new ArrayList<>();

        for (Long userId : candidateUserIds) {
            List<UserTimetable> snapshot = timetablesByUser
                    .getOrDefault(userId, List.of())
                    .stream()
                    .filter(entry -> !canceledIds.contains(entry.getSubject().getId()))
                    .toList();
            for (UserTimetable changedEntry : snapshot) {
                SubjectImportPlanResponse.ChangeItem change =
                        timeChanges.get(changedEntry.getSubject().getId());
                if (change == null) {
                    continue;
                }
                UserTimetable conflicting = snapshot.stream()
                        .filter(other -> !Objects.equals(other.getId(), changedEntry.getId()))
                        .filter(other -> overlaps(
                                change.after().schedules(),
                                proposedSchedules(other, timeChanges)))
                        .findFirst()
                        .orElse(null);
                if (conflicting != null) {
                    conflicts.add(new SubjectImportImpactResponse.ProjectedConflict(
                            userId,
                            change.courseCode(),
                            change.subjectName(),
                            conflicting.getSubject().getCourseCode(),
                            conflicting.getSubject().getSubjectName()));
                }
            }
        }
        return conflicts;
    }

    private List<SubjectImportPlanResponse.ScheduleSnapshot> proposedSchedules(
            UserTimetable entry,
            Map<Long, SubjectImportPlanResponse.ChangeItem> timeChanges) {
        SubjectImportPlanResponse.ChangeItem proposal =
                timeChanges.get(entry.getSubject().getId());
        return proposal == null
                ? snapshot(entry.getSubject()).schedules()
                : proposal.after().schedules();
    }

    private boolean overlaps(
            List<SubjectImportPlanResponse.ScheduleSnapshot> left,
            List<SubjectImportPlanResponse.ScheduleSnapshot> right) {
        return left.stream().anyMatch(first -> right.stream().anyMatch(second -> {
            if (!Objects.equals(first.dayOfWeek(), second.dayOfWeek())
                    || first.startTime() == null
                    || first.endTime() == null
                    || second.startTime() == null
                    || second.endTime() == null) {
                return false;
            }
            double firstEnd = first.startTime() > first.endTime() ? first.endTime() + 8 : first.endTime();
            double secondEnd = second.startTime() > second.endTime() ? second.endTime() + 8 : second.endTime();
            return first.startTime() < secondEnd && firstEnd > second.startTime();
        }));
    }

    private List<SubjectImportApplyResponse.VerificationItem> verifyApplied(
            List<SubjectImportPlanResponse.ChangeItem> selected,
            String semester) {
        return selected.stream()
                .map(change -> {
                    Subject current = subjectRepository
                            .findFirstByCourseCodeAndSemesterOrderByIdAsc(change.courseCode(), semester)
                            .orElse(null);
                    boolean matched = current != null
                            && sameSubjectData(snapshot(current), change.after());
                    return new SubjectImportApplyResponse.VerificationItem(
                            change.courseCode(),
                            matched,
                            matched ? "DB 반영값 일치" : "DB 반영값 불일치");
                })
                .toList();
    }

    private boolean isStale(
            SubjectImportPlanResponse.ChangeItem change,
            Subject current) {
        if (change.before() == null) {
            return current != null;
        }
        return current == null || !sameSubjectData(snapshot(current), change.before());
    }

    private boolean sameSubjectData(
            SubjectImportPlanResponse.SubjectSnapshot current,
            SubjectImportPlanResponse.SubjectSnapshot expected) {
        if (current == null || expected == null) {
            return current == expected;
        }
        return Objects.equals(current.courseCode(), expected.courseCode())
                && Objects.equals(current.semester(), expected.semester())
                && current.active() == expected.active()
                && Objects.equals(current.subjectName(), expected.subjectName())
                && Objects.equals(current.credits(), expected.credits())
                && Objects.equals(current.professor(), expected.professor())
                && Objects.equals(current.department(), expected.department())
                && Objects.equals(current.grade(), expected.grade())
                && Objects.equals(current.subjectType(), expected.subjectType())
                && Objects.equals(current.classMethod(), expected.classMethod())
                && current.night() == expected.night()
                && Objects.equals(current.schedules(), expected.schedules());
    }

    private void requireCurrentComparisonVersion(StoredPlan stored) {
        if (stored.comparisonVersion() != CURRENT_COMPARISON_VERSION) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "비교 기준이 변경된 이전 미리보기입니다. JSON을 다시 업로드해주세요.");
        }
    }

    private List<SubjectImportPlanResponse.ChangeItem> selectedChanges(
            StoredPlan stored,
            List<String> courseCodes) {
        if (courseCodes == null || courseCodes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "반영할 과목을 하나 이상 선택해주세요.");
        }
        Set<String> requested = new LinkedHashSet<>(courseCodes);
        List<SubjectImportPlanResponse.ChangeItem> selected = stored.changes().stream()
                .filter(change -> requested.contains(change.courseCode()))
                .toList();
        if (selected.size() != requested.size()) {
            Set<String> known = selected.stream()
                    .map(SubjectImportPlanResponse.ChangeItem::courseCode)
                    .collect(Collectors.toSet());
            requested.removeAll(known);
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "검토 계획에 없는 학수번호입니다: " + String.join(", ", requested));
        }
        return selected;
    }

    private SubjectImportPlan requirePreviewedPlan(String planId) {
        SubjectImportPlan plan = findPlan(planId);
        requirePreviewed(plan);
        return plan;
    }

    private SubjectImportPlan requirePreviewedPlanForApply(String planId) {
        SubjectImportPlan plan = planRepository.findByIdForUpdate(planId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "검토 계획을 찾을 수 없습니다."));
        requirePreviewed(plan);
        return plan;
    }

    private void requirePreviewed(SubjectImportPlan plan) {
        if (!STATUS_PREVIEWED.equals(plan.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 반영되었거나 사용할 수 없는 검토 계획입니다.");
        }
    }

    private SubjectImportPlan findPlan(String planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "검토 계획을 찾을 수 없습니다."));
    }

    private Map<String, Subject> indexSubjectsByCourseCode(List<Subject> subjects) {
        Map<String, Subject> byCode = new LinkedHashMap<>();
        Set<String> duplicates = new LinkedHashSet<>();
        for (Subject subject : subjects) {
            if (!hasText(subject.getCourseCode())) {
                continue;
            }
            if (byCode.putIfAbsent(subject.getCourseCode(), subject) != null) {
                duplicates.add(subject.getCourseCode());
            }
        }
        if (!duplicates.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "DB에 같은 학기·학수번호 과목이 중복되어 있습니다: "
                            + String.join(", ", duplicates));
        }
        return byCode;
    }

    private SubjectImportPlanResponse.SubjectSnapshot snapshot(Subject subject) {
        List<SubjectImportPlanResponse.ScheduleSnapshot> schedules = canonicalSchedules(
                subject.getSchedules().stream()
                        .map(schedule -> new SubjectImportPlanResponse.ScheduleSnapshot(
                                schedule.getDayOfWeek(),
                                schedule.getStartTime(),
                                schedule.getEndTime(),
                                schedule.getRoomSegments().stream()
                                        .map(room -> new SubjectImportPlanResponse.RoomSnapshot(
                                                room.getRoom(),
                                                room.getStartTime(),
                                                room.getEndTime()))
                                        .toList()))
                        .toList());
        return new SubjectImportPlanResponse.SubjectSnapshot(
                subject.getId(),
                subject.getCourseCode(),
                subject.getSemester(),
                Boolean.TRUE.equals(subject.getActive()),
                subject.getSubjectName(),
                subject.getCredits(),
                subject.getProfessor(),
                subject.getDepartment(),
                subject.getGrade(),
                subject.getSubjectType().name(),
                subject.getClassMethod().name(),
                Boolean.TRUE.equals(subject.getIsNight()),
                Boolean.TRUE.equals(subject.getSyllabusAvailable()),
                schedules);
    }

    private SubjectImportPlanResponse.SubjectSnapshot snapshot(
            OfficialSubjectImportService.OfficialSubjectRecord record) {
        List<SubjectImportPlanResponse.ScheduleSnapshot> schedules = canonicalSchedules(
                record.schedules().stream()
                        .map(schedule -> new SubjectImportPlanResponse.ScheduleSnapshot(
                                schedule.dayOfWeek(),
                                schedule.startTime(),
                                schedule.endTime(),
                                schedule.roomSegments().stream()
                                        .map(room -> new SubjectImportPlanResponse.RoomSnapshot(
                                                room.room(),
                                                room.startTime(),
                                                room.endTime()))
                                        .toList()))
                        .toList());
        return new SubjectImportPlanResponse.SubjectSnapshot(
                null,
                record.courseCode(),
                record.semester(),
                true,
                record.subjectName(),
                record.credits(),
                record.professor(),
                record.department(),
                record.grade(),
                record.subjectType().name(),
                record.classMethod().name(),
                Boolean.TRUE.equals(record.isNight()),
                Boolean.TRUE.equals(record.syllabusAvailable()),
                schedules);
    }

    private SubjectImportPlanResponse.SubjectSnapshot withActive(
            SubjectImportPlanResponse.SubjectSnapshot snapshot,
            boolean active) {
        return new SubjectImportPlanResponse.SubjectSnapshot(
                snapshot.id(),
                snapshot.courseCode(),
                snapshot.semester(),
                active,
                snapshot.subjectName(),
                snapshot.credits(),
                snapshot.professor(),
                snapshot.department(),
                snapshot.grade(),
                snapshot.subjectType(),
                snapshot.classMethod(),
                snapshot.night(),
                snapshot.syllabusAvailable(),
                snapshot.schedules());
    }

    private OfficialSubjectImportService.OfficialSubjectRecord toRecord(
            SubjectImportPlanResponse.SubjectSnapshot snapshot) {
        return new OfficialSubjectImportService.OfficialSubjectRecord(
                snapshot.courseCode(),
                snapshot.semester(),
                snapshot.subjectName(),
                snapshot.credits(),
                snapshot.professor(),
                snapshot.department(),
                snapshot.grade(),
                SubjectType.valueOf(snapshot.subjectType()),
                ClassMethod.valueOf(snapshot.classMethod()),
                snapshot.night(),
                snapshot.syllabusAvailable(),
                snapshot.schedules().stream()
                        .map(schedule -> new OfficialSubjectImportService.ScheduleValue(
                                schedule.dayOfWeek(),
                                schedule.startTime(),
                                schedule.endTime(),
                                schedule.rooms().stream()
                                        .map(room -> new OfficialSubjectImportService.RoomSegmentValue(
                                                room.room(),
                                                schedule.dayOfWeek(),
                                                room.startTime(),
                                                room.endTime()))
                                        .toList()))
                        .toList());
    }

    private void addField(
            List<SubjectImportPlanResponse.FieldChange> fields,
            String label,
            Object before,
            Object after) {
        if (!Objects.equals(before, after)) {
            fields.add(new SubjectImportPlanResponse.FieldChange(
                    label,
                    display(before),
                    display(after)));
        }
    }

    private String display(Object value) {
        if (value == null || "".equals(value)) {
            return "없음";
        }
        if (value instanceof Boolean bool) {
            return bool ? "예" : "아니오";
        }
        return String.valueOf(value);
    }

    private List<String> scheduleKeys(List<SubjectImportPlanResponse.ScheduleSnapshot> schedules) {
        return schedules.stream().map(this::scheduleKey).sorted().toList();
    }

    private List<String> roomKeys(List<SubjectImportPlanResponse.ScheduleSnapshot> schedules) {
        return schedules.stream()
                .flatMap(schedule -> schedule.rooms().stream())
                .map(SubjectImportPlanResponse.RoomSnapshot::room)
                .filter(this::hasText)
                .distinct()
                .sorted()
                .toList();
    }

    private String scheduleText(List<SubjectImportPlanResponse.ScheduleSnapshot> schedules) {
        if (schedules.isEmpty()) {
            return "시간 미정";
        }
        return schedules.stream()
                .map(schedule -> schedule.dayOfWeek() + " "
                        + TimeConverter.convertToClockTime(schedule.startTime())
                        + "-"
                        + TimeConverter.convertToClockTime(schedule.endTime()))
                .collect(Collectors.joining(", "));
    }

    private String roomText(List<SubjectImportPlanResponse.ScheduleSnapshot> schedules) {
        String value = schedules.stream()
                .flatMap(schedule -> schedule.rooms().stream())
                .map(SubjectImportPlanResponse.RoomSnapshot::room)
                .distinct()
                .collect(Collectors.joining(", "));
        return value.isBlank() ? "강의실 미정" : value;
    }

    private String scheduleKey(SubjectImportPlanResponse.ScheduleSnapshot schedule) {
        return schedule.dayOfWeek() + ":" + schedule.startTime() + "-" + schedule.endTime();
    }

    private String roomKey(SubjectImportPlanResponse.RoomSnapshot room) {
        return room.room() + ":" + room.startTime() + "-" + room.endTime();
    }

    private List<SubjectImportPlanResponse.ScheduleSnapshot> canonicalSchedules(
            List<SubjectImportPlanResponse.ScheduleSnapshot> schedules) {
        Comparator<Double> timeComparator = Comparator.nullsLast(Double::compareTo);
        Comparator<SubjectImportPlanResponse.RoomSnapshot> roomComparator =
                Comparator.comparing(
                                SubjectImportPlanResponse.RoomSnapshot::startTime,
                                timeComparator)
                        .thenComparing(
                                SubjectImportPlanResponse.RoomSnapshot::endTime,
                                timeComparator)
                        .thenComparing(
                                SubjectImportPlanResponse.RoomSnapshot::room,
                                Comparator.nullsLast(String::compareTo));
        Comparator<SubjectImportPlanResponse.ScheduleSnapshot> scheduleComparator =
                Comparator.comparingInt(
                                (SubjectImportPlanResponse.ScheduleSnapshot schedule) ->
                                        dayOrder(schedule.dayOfWeek()))
                        .thenComparing(
                                SubjectImportPlanResponse.ScheduleSnapshot::startTime,
                                timeComparator)
                        .thenComparing(
                                SubjectImportPlanResponse.ScheduleSnapshot::endTime,
                                timeComparator);

        return schedules.stream()
                .map(schedule -> new SubjectImportPlanResponse.ScheduleSnapshot(
                        schedule.dayOfWeek(),
                        schedule.startTime(),
                        schedule.endTime(),
                        schedule.rooms().stream().sorted(roomComparator).toList()))
                .distinct()
                .sorted(scheduleComparator)
                .toList();
    }

    private int dayOrder(String dayOfWeek) {
        int index = dayOfWeek == null ? -1 : DAYS.indexOf(dayOfWeek);
        return index < 0 ? DAYS.length() : index;
    }

    private List<String> warnings(
            List<OfficialSubjectImportService.OfficialSubjectRecord> records,
            List<SubjectImportPlanResponse.ChangeItem> changes) {
        List<String> warnings = new ArrayList<>();
        long noSchedule = records.stream().filter(record -> record.schedules().isEmpty()).count();
        if (noSchedule > 0) {
            warnings.add("시간 미정 또는 비동기 과목 " + noSchedule + "개가 포함되어 있습니다.");
        }
        long canceled = changes.stream()
                .filter(change -> change.categories().contains(SubjectImportChangeCategory.CANCELED))
                .count();
        if (canceled > 0) {
            warnings.add("폐강 " + canceled + "개는 선택 반영 시 active=false 처리되고 시간표에서 제외됩니다.");
        }
        return warnings;
    }

    private String normalizeSemester(String semester) {
        if (!hasText(semester) || !semester.matches("\\d{4}-(1|2|여름|겨울)")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "학기는 2026-2 형식으로 입력해주세요.");
        }
        return semester.trim();
    }

    private String safeFilename(String filename) {
        if (!hasText(filename)) {
            return "upload.json";
        }
        return filename.replaceAll("[\\r\\n\\\\/]", "_");
    }

    private LocalDateTime nowInKorea() {
        return BusinessTime.nowInKorea(clock);
    }

    private String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder result = new StringBuilder();
            for (byte value : digest) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("검토 계획을 저장하지 못했습니다.", exception);
        }
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("검토 계획을 읽지 못했습니다.", exception);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record StoredPlan(
            int comparisonVersion,
            List<SubjectImportPlanResponse.ChangeItem> changes,
            List<String> warnings) {
    }
}
