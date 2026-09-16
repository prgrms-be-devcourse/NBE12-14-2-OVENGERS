package com.ovengers.slotkey.access.repository;

import com.ovengers.slotkey.access.entity.DoorAccessToken;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
