package com.ovengers.slotkey.reservation.repository;

import com.ovengers.slotkey.reservation.entity.ReservationStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReservationStatusHistoryRepository extends JpaRepository<ReservationStatusHistory, Long> {

    List<ReservationStatusHistory> findAllByReservationIdOrderByChangedAtAsc(Long reservationId);
}
