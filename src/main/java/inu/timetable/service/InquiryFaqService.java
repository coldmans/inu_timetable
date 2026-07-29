package inu.timetable.service;

import inu.timetable.dto.AdminInquiryFaqResponse;
import inu.timetable.dto.InquiryFaqResponse;
import inu.timetable.dto.InquiryFaqUpsertRequest;
import inu.timetable.entity.InquiryFaq;
import inu.timetable.repository.InquiryFaqRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InquiryFaqService {

    private static final int MAX_QUESTION_LENGTH = 200;
    private static final int MAX_ANSWER_LENGTH = 2000;

    private final InquiryFaqRepository inquiryFaqRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<InquiryFaqResponse> getPublicFaqs() {
        return inquiryFaqRepository.findAllByActiveTrueOrderBySortOrderAscIdAsc().stream()
                .map(this::toPublicResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminInquiryFaqResponse> getAdminFaqs() {
        return inquiryFaqRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(this::toAdminResponse)
                .toList();
    }

    @Transactional
    public AdminInquiryFaqResponse create(InquiryFaqUpsertRequest request) {
        NormalizedFaq normalized = normalize(request);
        LocalDateTime now = LocalDateTime.now(clock);
        InquiryFaq saved = inquiryFaqRepository.save(InquiryFaq.builder()
                .question(normalized.question())
                .answer(normalized.answer())
                .sortOrder(normalized.sortOrder())
                .active(normalized.active())
                .createdAt(now)
                .updatedAt(now)
                .build());
        return toAdminResponse(saved);
    }

    @Transactional
    public AdminInquiryFaqResponse update(Long id, InquiryFaqUpsertRequest request) {
        NormalizedFaq normalized = normalize(request);
        InquiryFaq faq = inquiryFaqRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Q&A를 찾을 수 없습니다."));
        faq.update(
                normalized.question(),
                normalized.answer(),
                normalized.sortOrder(),
                normalized.active(),
                LocalDateTime.now(clock));
        return toAdminResponse(faq);
    }

    @Transactional
    public void delete(Long id) {
        if (!inquiryFaqRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Q&A를 찾을 수 없습니다.");
        }
        inquiryFaqRepository.deleteById(id);
    }

    private NormalizedFaq normalize(InquiryFaqUpsertRequest request) {
        if (request == null) {
            throw badRequest("Q&A 내용을 입력해주세요.");
        }
        String question = request.question() == null ? "" : request.question().trim();
        String answer = request.answer() == null ? "" : request.answer().trim();
        int sortOrder = request.sortOrder() == null ? 0 : request.sortOrder();
        boolean active = request.active() != null && request.active();

        if (question.isEmpty()) {
            throw badRequest("질문을 입력해주세요.");
        }
        if (question.length() > MAX_QUESTION_LENGTH) {
            throw badRequest("질문은 " + MAX_QUESTION_LENGTH + "자 이하로 입력해주세요.");
        }
        if (answer.isEmpty()) {
            throw badRequest("답변을 입력해주세요.");
        }
        if (answer.length() > MAX_ANSWER_LENGTH) {
            throw badRequest("답변은 " + MAX_ANSWER_LENGTH + "자 이하로 입력해주세요.");
        }
        if (sortOrder < 0) {
            throw badRequest("노출 순서는 0 이상이어야 합니다.");
        }
        return new NormalizedFaq(question, answer, sortOrder, active);
    }

    private ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    private InquiryFaqResponse toPublicResponse(InquiryFaq faq) {
        return new InquiryFaqResponse(faq.getId(), faq.getQuestion(), faq.getAnswer());
    }

    private AdminInquiryFaqResponse toAdminResponse(InquiryFaq faq) {
        return new AdminInquiryFaqResponse(
                faq.getId(),
                faq.getQuestion(),
                faq.getAnswer(),
                faq.getSortOrder(),
                faq.isActive(),
                faq.getCreatedAt(),
                faq.getUpdatedAt());
    }

    private record NormalizedFaq(
            String question,
            String answer,
            int sortOrder,
            boolean active) {
    }
}
