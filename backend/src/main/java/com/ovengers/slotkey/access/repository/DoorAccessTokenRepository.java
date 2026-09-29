package com.ovengers.slotkey.access.repository;

import com.ovengers.slotkey.access.entity.DoorAccessToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;

import java.time.LocalDateTime;
import java.util.Optional;

public interface DoorAccessTokenRepository extends JpaRepository<DoorAccessToken, Long> {

    // 토큰 해시로 출입 토큰 조회
    Optional<DoorAccessToken> findByTokenHash(
            String tokenHash
    );

    // 특정 예약의 활성 토큰 조회
    Optional<DoorAccessToken> findByReservationIdAndRevokedAtIsNull(
            Long reservationId
    );

    // REPEATABLE READ Read View를 우회하는 활성 토큰 상태 Locking Read
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM DoorAccessToken t WHERE t.reservation.id = :reservationId AND t.revokedAt IS NULL")
    Optional<DoorAccessToken> findByReservationIdAndRevokedAtIsNullForUpdate(@Param("reservationId") Long reservationId);

    // REPEATABLE READ Read View를 우회하는 최신 토큰 상태 Locking Read
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM DoorAccessToken t WHERE t.id = :id")
    Optional<DoorAccessToken> findByIdForUpdate(@Param("id") Long id);

    @Modifying(flushAutomatically = true)
    @Query("""
        UPDATE DoorAccessToken t
        SET t.revokedAt = :now,
            t.revokeReason = :reason
        WHERE t.revokedAt IS NULL
          AND t.reservation.id IN (
              SELECT r.id
              FROM Reservation r
              WHERE r.memberId = :memberId
          )
        """)
    int revokeAllByMemberId(
            @Param("memberId") Long memberId,
            @Param("now") LocalDateTime now,
            @Param("reason") String reason
    );
}
