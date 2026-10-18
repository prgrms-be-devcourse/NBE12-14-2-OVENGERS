package com.ovengers.slotkey.auth.repository;

import com.ovengers.slotkey.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository
        extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Query("""
        SELECT t FROM RefreshToken t
        JOIN FETCH t.member
        JOIN FETCH t.authSession
        WHERE t.tokenHash = :tokenHash
        """)
    Optional<RefreshToken> findWithSessionByTokenHash(
            @Param("tokenHash") String tokenHash
    );

    @Modifying(flushAutomatically = true)
    @Query("""
        UPDATE RefreshToken t
        SET t.revokedAt = :now
        WHERE t.authSession.id = :sessionId
          AND t.revokedAt IS NULL
        """)
    int revokeAllBySessionId(
            @Param("sessionId") String sessionId,
            @Param("now") LocalDateTime now
    );

    @Modifying(flushAutomatically = true)
    @Query("""
        UPDATE RefreshToken t
        SET t.revokedAt = :now
        WHERE t.member.id = :memberId
          AND t.revokedAt IS NULL
        """)
    int revokeAllByMemberId(
            @Param("memberId") Long memberId,
            @Param("now") LocalDateTime now
    );
}
