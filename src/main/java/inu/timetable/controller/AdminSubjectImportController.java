package inu.timetable.controller;

import inu.timetable.dto.SubjectImportApplyResponse;
import inu.timetable.dto.SubjectImportImpactResponse;
import inu.timetable.dto.SubjectImportPlanResponse;
import inu.timetable.dto.SubjectImportSelectionRequest;
import inu.timetable.service.AdminAuthService;
import inu.timetable.service.AdminOperationLockService;
import inu.timetable.service.SubjectImportReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

@RestController
@RequestMapping("/admin/api/subject-import-plans")
@RequiredArgsConstructor
@Tag(name = "관리자 과목 데이터 검토", description = "강의계획서 JSON 또는 종합강의시간표 Excel의 변경점과 사용자 영향을 검토한 뒤 선택 반영하는 API")
public class AdminSubjectImportController {

    private final AdminAuthService adminAuthService;
    private final AdminOperationLockService adminOperationLockService;
    private final SubjectImportReviewService subjectImportReviewService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "과목 파일 미리보기", description = "JSON 또는 Excel을 현재 DB와 비교해 변경 유형, before/after, 사용자 영향과 예상 충돌을 저장합니다.")
    public SubjectImportPlanResponse preview(
            @RequestPart("file") MultipartFile file,
            @RequestParam String semester,
            @RequestParam(defaultValue = "true") boolean deactivateMissing,
            HttpSession session) throws IOException {
        requireAuthenticated(session);
        return subjectImportReviewService.preview(file, semester, deactivateMissing);
    }

    @GetMapping("/{planId}")
    @Operation(summary = "저장된 검토 계획 조회")
    public SubjectImportPlanResponse get(
            @PathVariable String planId,
            HttpSession session) {
        requireAuthenticated(session);
        return subjectImportReviewService.get(planId);
    }

    @PostMapping("/{planId}/impact")
    @Operation(summary = "선택 과목 영향 재계산", description = "선택한 학수번호 기준으로 시간표·위시리스트 사용자와 예상 충돌을 다시 계산합니다.")
    public SubjectImportImpactResponse impact(
            @PathVariable String planId,
            @Valid @RequestBody SubjectImportSelectionRequest request,
            HttpSession session) {
        requireAuthenticated(session);
        return subjectImportReviewService.impact(planId, request.courseCodes());
    }

    @PostMapping("/{planId}/apply")
    @Operation(summary = "선택 과목 반영 및 재검증", description = "미리보기 이후 DB가 달라지지 않았을 때만 선택 과목을 반영하고 저장값을 다시 검증합니다.")
    public SubjectImportApplyResponse apply(
            @PathVariable String planId,
            @Valid @RequestBody SubjectImportSelectionRequest request,
            HttpSession session) throws IOException {
        requireAuthenticated(session);
        return adminOperationLockService.runExclusive(
                "subject-import-apply",
                () -> subjectImportReviewService.apply(planId, request.courseCodes()));
    }

    private void requireAuthenticated(HttpSession session) {
        if (!adminAuthService.hasAdminAccess(session)) {
            String reason = adminAuthService.isPasswordChangeRequired(session)
                    ? "Admin credential change required"
                    : "Admin login required";
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, reason);
        }
    }
}
