package com.ovengers.slotkey.reservation.repository;

import com.ovengers.slotkey.reservation.entity.ReservationSlot;
import com.ovengers.slotkey.reservation.entity.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ReservationSlotRepository extends JpaRepository<ReservationSlot, Long> {

    /** 특정 공간·날짜의 점유 슬롯 조회. space 도메인의 OccupiedSlotProvider 어댑터가 사용한다. */
    List<ReservationSlot> findAllBySpaceIdAndSlotStartBetween(
            Long spaceId, LocalDateTime start, LocalDateTime end);

    /**
     * 특정 공간 및 시간 범위(반개구간: startInclusive <= slotStart < endExclusive) 내에서
     * 실제 유효 점유 상태인 슬롯의 시작 시각 목록을 조회한다.
     * CONFIRMED, IN_USE, COMPLETED 상태이거나, HELD 상태이면서 아직 만료되지 않은(:now <
     * holdExpiresAt) 경우만 점유로 본다.
     * EXPIRED, CANCELLED, NO_SHOW 상태이거나 이미 만료된 HELD는 제외된다.
     */
    @Query("SELECT rs.slotStart FROM ReservationSlot rs, Reservation r " +
                    "WHERE rs.reservationId = r.id " +
                    "AND rs.spaceId = :spaceId " +
                    "AND rs.slotStart >= :startInclusive AND rs.slotStart < :endExclusive " +
                    "AND (r.status IN (:confirmedStatuses) OR (r.status = :heldStatus AND :now < r.holdExpiresAt))")
    List<LocalDateTime> findOccupiedSlotStarts(
                    @Param("spaceId") Long spaceId,
                    @Param("startInclusive") LocalDateTime startInclusive,
                    @Param("endExclusive") LocalDateTime endExclusive,
                    @Param("now") LocalDateTime now,
                    @Param("heldStatus") ReservationStatus heldStatus,
                    @Param("confirmedStatuses") List<ReservationStatus> confirmedStatuses);

    /** 요청한 슬롯 시작 시각들 중 이미 점유된 슬롯을 가진 예약 id들(중복 없이). */
    @Query("SELECT DISTINCT rs.reservationId FROM ReservationSlot rs " +
            "WHERE rs.spaceId = :spaceId AND rs.slotStart IN :slotStarts")
    List<Long> findReservationIdsBySpaceIdAndSlotStartIn(
            @Param("spaceId") Long spaceId,
            @Param("slotStarts") List<LocalDateTime> slotStarts);

    /**
     * 지금 막(혹은 이전에) EXPIRED로 전이된 예약들의 슬롯을 정리한다(core-domain-decisions 2-3).
     * 서브쿼리가 삭제 시점에 상태를 다시 확인하므로, ids 중 실제로는 아직 HELD/CONFIRMED인
     * 예약(경합에서 만료 전이에 실패한 예약)의 슬롯은 건드리지 않는다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ReservationSlot rs WHERE rs.reservationId IN " +
            "(SELECT r.id FROM Reservation r WHERE r.id IN :ids AND r.status = :expired)")
    int deleteSlotsOfExpiredReservations(
            @Param("ids") List<Long> ids,
            @Param("expired") ReservationStatus expired);

    /** 특정 예약의 슬롯 목록을 조회한다. */
    List<ReservationSlot> findAllByReservationId(Long reservationId);

    /** 취소/노쇼 처리 등에서 예약 하나의 슬롯을 전부 반환(삭제)할 때 사용한다. */
    long deleteByReservationId(Long reservationId);
}
