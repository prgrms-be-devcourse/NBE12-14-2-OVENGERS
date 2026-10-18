package com.ovengers.slotkey.auth.repository;

import com.ovengers.slotkey.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import jakarta.persistence.LockModeType;

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

    @Query("""
        SELECT t.id AS id, t.member.id AS memberId, t.authSession.id AS sessionId
        FROM RefreshToken t
        WHERE t.tokenHash = :tokenHash
        """)
    Optional<RefreshTokenReference> findReferenceByTokenHash(
            @Param("tokenHash") String tokenHash
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM RefreshToken t WHERE t.id = :tokenId")
    Optional<RefreshToken> findByIdForUpdate(@Param("tokenId") Long tokenId);

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

    interface RefreshTokenReference {
        Long getId();
        Long getMemberId();
        String getSessionId();
    }
}
