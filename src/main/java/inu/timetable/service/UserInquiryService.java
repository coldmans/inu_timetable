package inu.timetable.service;

import inu.timetable.dto.AdminInquiryPageResponse;
import inu.timetable.dto.AdminInquiryResponse;
import inu.timetable.entity.User;
import inu.timetable.entity.UserInquiry;
import inu.timetable.repository.UserInquiryRepository;
import inu.timetable.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 사이트 내 문의 접수/관리.
 * 기존 인스타그램 DM 링크 문의를 대체하며, 비로그인 문의도 허용한다.
 */
@Service
@RequiredArgsConstructor
public class UserInquiryService {

    private static final String RATE_LIMIT_NAMESPACE = "INQUIRY";
    private static final int MAX_CONTENT_LENGTH = 1000;
    private static final int MAX_CONTACT_LENGTH = 100;

    private final UserInquiryRepository userInquiryRepository;
    private final UserRepository userRepository;
    private final LoginAttemptStore loginAttemptStore;
    private final Clock clock;

    @Value("${inquiry.rate-limit.max-submissions:10}")
    private int maxSubmissions;

    @Value("${inquiry.rate-limit.block-minutes:60}")
    private long blockMinutes;

    @Transactional
    public void submit(Long userId, String rawContent, String rawContact, HttpServletRequest request) {
        String content = rawContent == null ? "" : rawContent.trim();
        if (content.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "문의 내용을 입력해주세요.");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "문의 내용은 " + MAX_CONTENT_LENGTH + "자 이하로 입력해주세요.");
        }
        String contact = rawContact == null || rawContact.isBlank() ? null : rawContact.trim();
        if (contact != null && contact.length() > MAX_CONTACT_LENGTH) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "연락처는 " + MAX_CONTACT_LENGTH + "자 이하로 입력해주세요.");
        }

        // 비로그인도 접수 가능한 공개 엔드포인트라 IP 단위로 접수 횟수를 제한한다.
        // LoginAttemptStore 의 실패 카운터를 '접수 카운터'로 재사용해 인스턴스 간에도 공유된다.
        String clientIp = request.getRemoteAddr();
        if (loginAttemptStore.isBlocked(RATE_LIMIT_NAMESPACE, clientIp)) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS, "문의가 너무 자주 접수되었습니다. 잠시 후 다시 시도해주세요.");
        }
        loginAttemptStore.recordFailure(
                RATE_LIMIT_NAMESPACE, clientIp, maxSubmissions, Duration.ofMinutes(blockMinutes));

        userInquiryRepository.save(UserInquiry.builder()
                .userId(userId)
                .content(content)
                .contact(contact)
                .createdAt(LocalDateTime.now(clock))
                .build());
    }

    @Transactional(readOnly = true)
    public AdminInquiryPageResponse getInquiries(int page, int size, boolean unresolvedOnly) {
        Pageable pageable = PageRequest.of(page, size);
        Page<UserInquiry> inquiryPage = unresolvedOnly
                ? userInquiryRepository.findAllByResolvedAtIsNullOrderByCreatedAtDescIdDesc(pageable)
                : userInquiryRepository.findAllByOrderByCreatedAtDescIdDesc(pageable);

        Map<Long, String> usernamesById = loadUsernames(inquiryPage.getContent());
        List<AdminInquiryResponse> inquiries = inquiryPage.getContent().stream()
                .map(inquiry -> AdminInquiryResponse.fromEntity(
                        inquiry, usernamesById.get(inquiry.getUserId())))
                .toList();

        return new AdminInquiryPageResponse(
                inquiries,
                page,
                size,
                inquiryPage.getTotalElements(),
                inquiryPage.getTotalPages(),
                userInquiryRepository.countByResolvedAtIsNull());
    }

    @Transactional
    public void resolve(Long inquiryId) {
        UserInquiry inquiry = userInquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "문의를 찾을 수 없습니다."));
        inquiry.resolve(LocalDateTime.now(clock));
    }

    private Map<Long, String> loadUsernames(List<UserInquiry> inquiries) {
        List<Long> userIds = inquiries.stream()
                .map(UserInquiry::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername, (first, second) -> first));
    }
}
