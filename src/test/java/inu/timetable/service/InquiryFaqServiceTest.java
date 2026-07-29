package inu.timetable.service;

import inu.timetable.dto.AdminInquiryFaqResponse;
import inu.timetable.dto.InquiryFaqResponse;
import inu.timetable.dto.InquiryFaqUpsertRequest;
import inu.timetable.entity.InquiryFaq;
import inu.timetable.repository.InquiryFaqRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InquiryFaqServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-29T04:00:00Z"),
            ZoneId.of("Asia/Seoul"));

    private InquiryFaqRepository inquiryFaqRepository;
    private InquiryFaqService inquiryFaqService;

    @BeforeEach
    void setUp() {
        inquiryFaqRepository = mock(InquiryFaqRepository.class);
        inquiryFaqService = new InquiryFaqService(inquiryFaqRepository, FIXED_CLOCK);
    }

    @Test
    void getPublicFaqsReturnsOnlyActiveFaqsInDisplayOrder() {
        when(inquiryFaqRepository.findAllByActiveTrueOrderBySortOrderAscIdAsc())
                .thenReturn(List.of(
                        InquiryFaq.builder()
                                .id(2L)
                                .question("과목이 검색되지 않아요")
                                .answer("공식 데이터 반영 여부를 확인해주세요.")
                                .sortOrder(10)
                                .active(true)
                                .createdAt(LocalDateTime.of(2026, 7, 29, 12, 0))
                                .updatedAt(LocalDateTime.of(2026, 7, 29, 12, 0))
                                .build()));

        List<InquiryFaqResponse> response = inquiryFaqService.getPublicFaqs();

        assertThat(response).containsExactly(new InquiryFaqResponse(
                2L,
                "과목이 검색되지 않아요",
                "공식 데이터 반영 여부를 확인해주세요."));
    }

    @Test
    void createTrimsContentAndDefaultsToPrivateDraft() {
        when(inquiryFaqRepository.save(any(InquiryFaq.class)))
                .thenAnswer(invocation -> {
                    InquiryFaq faq = invocation.getArgument(0);
                    return InquiryFaq.builder()
                            .id(11L)
                            .question(faq.getQuestion())
                            .answer(faq.getAnswer())
                            .sortOrder(faq.getSortOrder())
                            .active(faq.isActive())
                            .createdAt(faq.getCreatedAt())
                            .updatedAt(faq.getUpdatedAt())
                            .build();
                });

        AdminInquiryFaqResponse response = inquiryFaqService.create(
                new InquiryFaqUpsertRequest("  질문  ", "  답변  ", null, null));

        ArgumentCaptor<InquiryFaq> captor = ArgumentCaptor.forClass(InquiryFaq.class);
        verify(inquiryFaqRepository).save(captor.capture());
        InquiryFaq saved = captor.getValue();
        assertThat(saved.getQuestion()).isEqualTo("질문");
        assertThat(saved.getAnswer()).isEqualTo("답변");
        assertThat(saved.getSortOrder()).isZero();
        assertThat(saved.isActive()).isFalse();
        assertThat(saved.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 7, 29, 13, 0));
        assertThat(saved.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 7, 29, 13, 0));
        assertThat(response.id()).isEqualTo(11L);
    }

    @Test
    void updateChangesExistingFaq() {
        InquiryFaq faq = InquiryFaq.builder()
                .id(7L)
                .question("기존 질문")
                .answer("기존 답변")
                .sortOrder(0)
                .active(true)
                .createdAt(LocalDateTime.of(2026, 7, 28, 12, 0))
                .updatedAt(LocalDateTime.of(2026, 7, 28, 12, 0))
                .build();
        when(inquiryFaqRepository.findById(7L)).thenReturn(Optional.of(faq));

        AdminInquiryFaqResponse response = inquiryFaqService.update(
                7L,
                new InquiryFaqUpsertRequest(" 새 질문 ", " 새 답변 ", 20, false));

        assertThat(response.question()).isEqualTo("새 질문");
        assertThat(response.answer()).isEqualTo("새 답변");
        assertThat(response.sortOrder()).isEqualTo(20);
        assertThat(response.active()).isFalse();
        assertThat(response.updatedAt()).isEqualTo(LocalDateTime.of(2026, 7, 29, 13, 0));
    }

    @Test
    void createRejectsInvalidContent() {
        assertThatThrownBy(() -> inquiryFaqService.create(
                new InquiryFaqUpsertRequest(" ", "답변", 0, true)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> inquiryFaqService.create(
                new InquiryFaqUpsertRequest("질문", " ", 0, true)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> inquiryFaqService.create(
                new InquiryFaqUpsertRequest("질문", "답변", -1, true)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(inquiryFaqRepository, never()).save(any());
    }

    @Test
    void deleteRejectsUnknownFaq() {
        when(inquiryFaqRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> inquiryFaqService.delete(99L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(inquiryFaqRepository, never()).deleteById(any());
    }
}
