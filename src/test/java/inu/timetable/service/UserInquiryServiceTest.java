package inu.timetable.service;

import inu.timetable.dto.AdminInquiryPageResponse;
import inu.timetable.entity.User;
import inu.timetable.entity.UserInquiry;
import inu.timetable.repository.UserInquiryRepository;
import inu.timetable.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserInquiryServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-28T03:00:00Z"),
            ZoneId.of("Asia/Seoul"));

    private UserInquiryRepository userInquiryRepository;
    private UserRepository userRepository;
    private LoginAttemptStore loginAttemptStore;
    private HttpServletRequest request;
    private UserInquiryService userInquiryService;

    @BeforeEach
    void setUp() {
        userInquiryRepository = mock(UserInquiryRepository.class);
        userRepository = mock(UserRepository.class);
        loginAttemptStore = mock(LoginAttemptStore.class);
        request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        userInquiryService = new UserInquiryService(
                userInquiryRepository, userRepository, loginAttemptStore, FIXED_CLOCK);
        ReflectionTestUtils.setField(userInquiryService, "maxSubmissions", 10);
        ReflectionTestUtils.setField(userInquiryService, "blockMinutes", 60L);
    }

    @Test
    void submitSavesTrimmedInquiryAndCountsSubmission() {
        userInquiryService.submit(42L, "  시간표가 안 보여요  ", "  ", request);

        ArgumentCaptor<UserInquiry> inquiryCaptor = ArgumentCaptor.forClass(UserInquiry.class);
        verify(userInquiryRepository).save(inquiryCaptor.capture());
        UserInquiry saved = inquiryCaptor.getValue();
        assertThat(saved.getUserId()).isEqualTo(42L);
        assertThat(saved.getContent()).isEqualTo("시간표가 안 보여요");
        assertThat(saved.getContact()).isNull();
        assertThat(saved.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 7, 28, 12, 0));
        assertThat(saved.getResolvedAt()).isNull();
        verify(loginAttemptStore).recordFailure("INQUIRY", "10.0.0.1", 10, Duration.ofMinutes(60));
    }

    @Test
    void submitAllowsAnonymousInquiryWithContact() {
        userInquiryService.submit(null, "회원가입이 안 돼요", "insta@someone", request);

        ArgumentCaptor<UserInquiry> inquiryCaptor = ArgumentCaptor.forClass(UserInquiry.class);
        verify(userInquiryRepository).save(inquiryCaptor.capture());
        assertThat(inquiryCaptor.getValue().getUserId()).isNull();
        assertThat(inquiryCaptor.getValue().getContact()).isEqualTo("insta@someone");
    }

    @Test
    void submitRejectsBlankContent() {
        assertThatThrownBy(() -> userInquiryService.submit(1L, "   ", null, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(userInquiryRepository, never()).save(any());
    }

    @Test
    void submitRejectsTooLongContent() {
        assertThatThrownBy(() -> userInquiryService.submit(1L, "a".repeat(1001), null, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(userInquiryRepository, never()).save(any());
    }

    @Test
    void submitRejectsWhenRateLimited() {
        when(loginAttemptStore.isBlocked("INQUIRY", "10.0.0.1")).thenReturn(true);

        assertThatThrownBy(() -> userInquiryService.submit(1L, "문의합니다", null, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        verify(userInquiryRepository, never()).save(any());
    }

    @Test
    void getInquiriesMapsUsernamesAndUnresolvedCount() {
        UserInquiry memberInquiry = UserInquiry.builder()
                .id(1L).userId(42L).content("질문 있어요")
                .createdAt(LocalDateTime.of(2026, 7, 28, 11, 0)).build();
        UserInquiry anonymousInquiry = UserInquiry.builder()
                .id(2L).content("비로그인 문의")
                .createdAt(LocalDateTime.of(2026, 7, 28, 10, 0)).build();
        when(userInquiryRepository.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(memberInquiry, anonymousInquiry), PageRequest.of(0, 20), 2));
        when(userRepository.findAllById(anyList()))
                .thenReturn(List.of(User.builder().id(42L).username("student").password("pw").build()));
        when(userInquiryRepository.countByResolvedAtIsNull()).thenReturn(2L);

        AdminInquiryPageResponse response = userInquiryService.getInquiries(0, 20, false);

        assertThat(response.inquiries()).hasSize(2);
        assertThat(response.inquiries().get(0).username()).isEqualTo("student");
        assertThat(response.inquiries().get(1).username()).isNull();
        assertThat(response.unresolvedCount()).isEqualTo(2L);
        assertThat(response.totalElements()).isEqualTo(2L);
    }

    @Test
    void resolveMarksInquiryResolvedOnce() {
        UserInquiry inquiry = UserInquiry.builder()
                .id(1L).content("문의").createdAt(LocalDateTime.of(2026, 7, 27, 9, 0)).build();
        when(userInquiryRepository.findById(1L)).thenReturn(Optional.of(inquiry));

        userInquiryService.resolve(1L);
        assertThat(inquiry.getResolvedAt()).isEqualTo(LocalDateTime.of(2026, 7, 28, 12, 0));

        // 이미 처리된 문의는 처리 시각을 덮어쓰지 않는다.
        ReflectionTestUtils.setField(inquiry, "resolvedAt", LocalDateTime.of(2026, 7, 27, 10, 0));
        userInquiryService.resolve(1L);
        assertThat(inquiry.getResolvedAt()).isEqualTo(LocalDateTime.of(2026, 7, 27, 10, 0));
    }

    @Test
    void resolveRejectsUnknownInquiry() {
        when(userInquiryRepository.findById(eq(99L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userInquiryService.resolve(99L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
