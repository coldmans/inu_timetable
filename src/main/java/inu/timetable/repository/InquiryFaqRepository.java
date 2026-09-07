package inu.timetable.repository;

import inu.timetable.entity.InquiryFaq;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InquiryFaqRepository extends JpaRepository<InquiryFaq, Long> {

    List<InquiryFaq> findAllByActiveTrueOrderBySortOrderAscIdAsc();

    List<InquiryFaq> findAllByOrderBySortOrderAscIdAsc();
}
