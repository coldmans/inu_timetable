package inu.timetable.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 문의 작성 전에 노출하는 관리자 작성 Q&A.
 * 초기 데이터는 만들지 않고, 관리자가 등록한 활성 항목만 사용자에게 노출한다.
 */
@Entity
@Table(
        name = "inquiry_faqs",
        indexes = @Index(
                name = "idx_inquiry_faqs_active_order",
                columnList = "active, sort_order, id"))
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InquiryFaq {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String question;

    @Column(nullable = false, length = 2000)
    private String answer;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void update(
            String question,
            String answer,
            int sortOrder,
            boolean active,
            LocalDateTime updatedAt) {
        this.question = question;
        this.answer = answer;
        this.sortOrder = sortOrder;
        this.active = active;
        this.updatedAt = updatedAt;
    }
}
