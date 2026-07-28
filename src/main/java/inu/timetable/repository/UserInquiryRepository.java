package inu.timetable.repository;

import inu.timetable.entity.UserInquiry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserInquiryRepository extends JpaRepository<UserInquiry, Long> {

    Page<UserInquiry> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    Page<UserInquiry> findAllByResolvedAtIsNullOrderByCreatedAtDescIdDesc(Pageable pageable);

    long countByResolvedAtIsNull();
}
