package com.ovengers.slotkey.reservation.repository;

import com.ovengers.slotkey.reservation.entity.Reservation;
import com.ovengers.slotkey.reservation.entity.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /**
     * 결제 확인의 "문지기"(§2-1, §6-2). HELD이고 아직 만료 전일 때만 CONFIRMED로 전이한다.
     * 영향 행이 0이면 이미 만료되었거나 이미 처리된 요청이다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Reservation r SET r.status = :confirmed " +
            "WHERE r.id = :id AND r.status = :held AND :now < r.holdExpiresAt")
    int confirmIfHeldAndNotExpired(
            @Param("id") Long id,
            @Param("now") LocalDateTime now,
            @Param("held") ReservationStatus held,
            @Param("confirmed") ReservationStatus confirmed
    );

    /**
     * 만료된 HELD를 정리한다(§2-3). 배치가 아니라 예약 생성 시점에도 호출되어
     * "만료된 행은 점유가 아니다"를 보장한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Reservation r SET r.status = :expired " +
            "WHERE r.id IN :ids AND r.status = :held AND r.holdExpiresAt < :now")
    int expireHeldReservations(
            @Param("ids") List<Long> ids,
            @Param("now") LocalDateTime now,
            @Param("held") ReservationStatus held,
            @Param("expired") ReservationStatus expired
    );

    /** 체크아웃/자동 퇴실의 문지기(§8-4). IN_USE일 때만 COMPLETED로 전이한다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Reservation r SET r.status = :completed, r.checkedOutAt = :checkedOutAt " +
            "WHERE r.id = :id AND r.status = :inUse")
    int checkOutIfInUse(
            @Param("id") Long id,
            @Param("checkedOutAt") LocalDateTime checkedOutAt,
            @Param("inUse") ReservationStatus inUse,
            @Param("completed") ReservationStatus completed
    );

    /**
     * 연장의 문지기(§4-3, §7-2). end_time 낙관적 검사 + 상태 검사를 한 번에 수행한다.
     * 연장↔연장 중복 제출은 expectedEndTime 불일치로, 연장↔취소/체크아웃 경합은
     * status 조건으로 각각 막는다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Reservation r SET r.endTime = :newEndTime, r.totalAmount = :newTotalAmount " +
            "WHERE r.id = :id AND r.endTime = :expectedEndTime " +
            "AND r.status IN (:confirmed, :inUse)")
    int extendIfEndTimeMatches(
            @Param("id") Long id,
            @Param("expectedEndTime") LocalDateTime expectedEndTime,
            @Param("newEndTime") LocalDateTime newEndTime,
            @Param("newTotalAmount") Integer newTotalAmount,
            @Param("confirmed") ReservationStatus confirmed,
            @Param("inUse") ReservationStatus inUse
    );
}
