package com.ovengers.slotkey.reservation.repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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
     * 결제 확인의 "문지기"(core-domain-decisions 2-1, core-domain-decisions 6-2). HELD이고 아직 만료 전일 때만 CONFIRMED로 전이한다.
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
     * 만료된 HELD를 정리한다(core-domain-decisions 2-3). 배치가 아니라 예약 생성 시점에도 호출되어
     * "만료된 행은 점유가 아니다"를 보장한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Reservation r SET r.status = :expired " +
                    "WHERE r.id IN :ids AND r.status = :held AND r.holdExpiresAt <= :now")
    int expireHeldReservations(
            @Param("ids") List<Long> ids,
            @Param("now") LocalDateTime now,
            @Param("held") ReservationStatus held,
            @Param("expired") ReservationStatus expired
    );

    /**
     * 취소의 문지기(§9). CONFIRMED이고 아직 시작 전일 때만 CANCELLED로 전이한다.
     * 영향 행이 0이면 이미 취소/완료되었거나 이미 시작된 예약이다(체크아웃으로만 종료 가능).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Reservation r SET r.status = :cancelled, r.cancelledAt = :now " +
            "WHERE r.id = :id AND r.status = :confirmed AND :now < r.startTime")
    int cancelIfConfirmedAndBeforeStart(
            @Param("id") Long id,
            @Param("now") LocalDateTime now,
            @Param("confirmed") ReservationStatus confirmed,
            @Param("cancelled") ReservationStatus cancelled
    );

    /** 체크아웃/자동 퇴실의 문지기(core-domain-decisions 8-4). IN_USE일 때만 COMPLETED로 전이한다. */
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
     * 연장의 문지기(core-domain-decisions 4-3, core-domain-decisions 7-2). end_time 낙관적 검사 + 상태 검사를 한 번에 수행한다.
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

    // ===== 조회 (ReservationQueryService) =====
    Page<Reservation> findAllByMemberId(Long memberId, Pageable pageable);
    Page<Reservation> findAllByMemberIdAndStatus(Long memberId, ReservationStatus status, Pageable pageable);

    // ===== 배치 대상 조회 (ReservationCompletionScheduler) =====
    // 후보 id만 뽑고, 실제 전이 여부는 건별 조건부 UPDATE가 최종 판정한다.

    @Query("SELECT r.id FROM Reservation r WHERE r.status = :held AND r.holdExpiresAt <= :now")
    List<Long> findExpiredHoldIds(
            @Param("now") LocalDateTime now,
            @Param("held") ReservationStatus held
    );

    @Query("SELECT r.id FROM Reservation r WHERE r.status = :confirmed AND r.startTime < :startTimeBefore")
    List<Long> findNoShowCandidateIds(
            @Param("startTimeBefore") LocalDateTime startTimeBefore,
            @Param("confirmed") ReservationStatus confirmed
    );

    @Query("SELECT r.id FROM Reservation r WHERE r.status = :inUse AND r.endTime <= :now")
    List<Long> findAutoCheckOutCandidateIds(
            @Param("now") LocalDateTime now,
            @Param("inUse") ReservationStatus inUse
    );

    /**
     * 노쇼 판정의 문지기(core-domain-decisions 6-3, core-domain-decisions 8-1). 체크인 마감(start + 15분)이 지났는데 아직 CONFIRMED일 때만
     * NO_SHOW로 전이한다. 같은 순간 체크인(CONFIRMED -> IN_USE)이 먼저 성공했다면 영향 행 0.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Reservation r SET r.status = :noShow " +
            "WHERE r.id = :id AND r.status = :confirmed AND r.startTime < :startTimeBefore")
    int markNoShowIfNotCheckedIn(
            @Param("id") Long id,
            @Param("startTimeBefore") LocalDateTime startTimeBefore,
            @Param("confirmed") ReservationStatus confirmed,
            @Param("noShow") ReservationStatus noShow
    );

    /**
     * 자동 퇴실의 문지기(core-domain-decisions 3-4). checked_out_at에는 배치 실행 시각이 아니라 end_time을 넣는다.
     * end_time 조건을 UPDATE에도 다시 거는 이유: 후보 조회 이후 연장(end_time 변경)이 먼저
     * 커밋됐다면, 늘어난 이용 시간을 배치가 잘라먹지 않도록 영향 행 0으로 끝내기 위함.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Reservation r SET r.status = :completed, r.checkedOutAt = r.endTime " +
            "WHERE r.id = :id AND r.status = :inUse AND r.endTime <= :now")
    int autoCheckOutIfEnded(
            @Param("id") Long id,
            @Param("now") LocalDateTime now,
            @Param("inUse") ReservationStatus inUse,
            @Param("completed") ReservationStatus completed
    );
}
