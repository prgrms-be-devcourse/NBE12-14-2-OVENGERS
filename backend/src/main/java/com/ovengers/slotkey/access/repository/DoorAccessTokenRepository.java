package com.ovengers.slotkey.access.repository;

import com.ovengers.slotkey.access.entity.DoorAccessToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
