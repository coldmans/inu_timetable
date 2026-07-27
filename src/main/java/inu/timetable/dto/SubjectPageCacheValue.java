package inu.timetable.dto;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * Redis-safe representation of a subject page.
 *
 * <p>Spring Data's {@code PageImpl} is an API model rather than a stable serialization
 * contract, so only the values needed to reconstruct it are cached.</p>
 */
public record SubjectPageCacheValue(
        List<SubjectDto> content,
        int page,
        int size,
        long totalElements) {

    public SubjectPageCacheValue {
        content = new ArrayList<>(content);
    }

    public Page<SubjectDto> toPage() {
        return new PageImpl<>(
                new ArrayList<>(content),
                PageRequest.of(page, size),
                totalElements);
    }
}
