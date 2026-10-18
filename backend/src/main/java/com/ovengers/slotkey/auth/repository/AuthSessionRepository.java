package com.ovengers.slotkey.auth.repository;

import com.ovengers.slotkey.auth.entity.AuthSession;
import com.ovengers.slotkey.member.entity.MemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AuthSessionRepository extends JpaRepository<AuthSession, String> {

    @Query("""
            select (count(s) > 0)
            from AuthSession s join s.member m
            where s.id = :sessionId
              and s.revokedAt is null
              and s.expiresAt > :now
              and m.id = :memberId
              and m.status = :activeStatus
            """)
    boolean isUsable(
            @Param("sessionId") String sessionId,
            @Param("memberId") Long memberId,
            @Param("now") LocalDateTime now,
            @Param("activeStatus") MemberStatus activeStatus
    );
}
