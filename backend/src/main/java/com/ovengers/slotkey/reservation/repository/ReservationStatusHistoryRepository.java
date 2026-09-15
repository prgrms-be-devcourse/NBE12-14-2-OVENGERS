package com.ovengers.slotkey.reservation.repository;

import com.ovengers.slotkey.reservation.entity.ReservationStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReservationStatusHistoryRepository extends JpaRepository<ReservationStatusHistory, Long> {
    // 예약 ID 기준 상태 변경 이력 시간순 조회
    List<ReservationStatusHistory> findAllByReservation_IdOrderByChangedAtAsc(Long reservationId);

}
