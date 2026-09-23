package com.ovengers.slotkey.inquiry.repository;

import com.ovengers.slotkey.inquiry.entity.Inquiry;
import com.ovengers.slotkey.inquiry.entity.InquiryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

    /** 회원 본인의 문의 목록(마이페이지 Q&A). */
    Page<Inquiry> findAllByMemberId(Long memberId, Pageable pageable);

    /** 관리자 문의 목록 상태 필터. */
    Page<Inquiry> findAllByStatus(InquiryStatus status, Pageable pageable);
}
