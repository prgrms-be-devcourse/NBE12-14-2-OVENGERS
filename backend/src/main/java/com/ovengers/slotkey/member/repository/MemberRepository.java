package com.ovengers.slotkey.member.repository;

import com.ovengers.slotkey.member.entity.Member;

import com.ovengers.slotkey.member.entity.MemberStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByEmail(String email);
    boolean existsByEmail(String email);


    @Query("SELECT m FROM Member m " +
            "WHERE (:status IS NULL OR m.status = :status) " +
            "AND (:keyword IS NULL OR :keyword = '' OR m.email " +
            "LIKE %:keyword% OR m.nickname LIKE %:keyword%)")
    Page<Member> searchMembers(
            @Param("keyword") String keyword,
            @Param("status") MemberStatus status,
            Pageable pageable);
}
