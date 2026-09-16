package com.ovengers.slotkey.access.repository;

import com.ovengers.slotkey.access.entity.AccessResult;
import com.ovengers.slotkey.access.entity.DoorAccessLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DoorAccessLogRepository extends JpaRepository<DoorAccessLog, Long> {

    // 특정 예약의 출입 로그 최신순으로 조회
    List<DoorAccessLog> findAllByReservationIdOrderByAttemptedAtDesc(
            Long reservationId
    );

    // 특정 예약의 출입 결과 존재 여부 확인
    boolean existsByReservationIdAndResult(
            Long reservationId,
            AccessResult result
    );
}
