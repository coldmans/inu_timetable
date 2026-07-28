package inu.timetable.controller;

import inu.timetable.dto.OfficialSubjectImportResponse;
import inu.timetable.dto.SubjectManagementRequest;
import inu.timetable.dto.SubjectManagementResponse;
import inu.timetable.service.AdminAccessGuard;
import inu.timetable.service.AdminOperationLockService;
import inu.timetable.service.OfficialSubjectImportService;
import inu.timetable.service.SubjectAdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/api/subjects")
@RequiredArgsConstructor
public class AdminSubjectController {

    private final AdminAccessGuard adminAccessGuard;
    private final AdminOperationLockService adminOperationLockService;
    private final SubjectAdminService subjectAdminService;
    private final OfficialSubjectImportService officialSubjectImportService;

    @PostMapping
    public ResponseEntity<SubjectManagementResponse> createSubject(
            HttpServletRequest servletRequest,
            @Valid @RequestBody SubjectManagementRequest request) {
        adminAccessGuard.requireAuthenticated(servletRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(subjectAdminService.createSubject(request));
    }

    @GetMapping("/{id}")
    public SubjectManagementResponse getSubject(
            HttpServletRequest servletRequest,
            @PathVariable Long id) {
        adminAccessGuard.requireAuthenticated(servletRequest);
        return subjectAdminService.getSubject(id);
    }

    @GetMapping("/semesters")
    public List<String> getSemesters(HttpServletRequest servletRequest) {
        adminAccessGuard.requireAuthenticated(servletRequest);
        return subjectAdminService.getSemesters();
    }

    @PutMapping("/{id}")
    public SubjectManagementResponse updateSubject(
            HttpServletRequest servletRequest,
            @PathVariable Long id,
            @Valid @RequestBody SubjectManagementRequest request) {
        adminAccessGuard.requireAuthenticated(servletRequest);
        return subjectAdminService.updateSubject(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteSubject(
            HttpServletRequest servletRequest,
            @PathVariable Long id) {
        adminAccessGuard.requireAuthenticated(servletRequest);
        subjectAdminService.deleteSubject(id);
        return ResponseEntity.ok(Map.of("deleted", true, "id", id));
    }

    @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public OfficialSubjectImportResponse previewOfficialExcelImport(
            HttpServletRequest servletRequest,
            @RequestParam("file") MultipartFile file,
            @RequestParam String semester) throws IOException {
        adminAccessGuard.requireAuthenticated(servletRequest);
        return officialSubjectImportService.preview(file, semester);
    }

    @PostMapping(value = "/import/apply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public OfficialSubjectImportResponse applyOfficialExcelImport(
            HttpServletRequest servletRequest,
            @RequestParam("file") MultipartFile file,
            @RequestParam String semester,
            @RequestParam(defaultValue = "true") boolean deactivateMissing) throws IOException {
        adminAccessGuard.requireAuthenticated(servletRequest);
        return adminOperationLockService.runExclusive("subject-import-apply",
                () -> officialSubjectImportService.apply(file, semester, deactivateMissing));
    }

    @PostMapping("/manual")
    public List<SubjectManagementResponse> addSubjectsManually(
            HttpServletRequest servletRequest,
            @RequestBody List<Map<String, Object>> subjectsData) {
        adminAccessGuard.requireAuthenticated(servletRequest);
        return subjectAdminService.addSubjectsManually(subjectsData);
    }
}
