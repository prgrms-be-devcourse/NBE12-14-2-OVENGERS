package com.ovengers.slotkey.reservation.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 예약 상태 전이 이력. 성공한 전이만 기록한다 — 조건부 UPDATE의 영향 행이 1일 때만
 * 같은 트랜잭션에서 INSERT한다(§3-1).
 */
@Entity
@Table(name = "reservation_status_history")
@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ReservationStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reservation_id", nullable = false)
    private Long reservationId;

    @Column(name = "changed_by_member_id")
    private Long changedByMemberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private ReservationStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private ReservationStatus toStatus;

    @Column(length = 255)
    private String reason;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    public static ReservationStatusHistory of(
            Long reservationId,
            Long changedByMemberId,
            ReservationStatus fromStatus,
            ReservationStatus toStatus,
            String reason,
            LocalDateTime changedAt
    ) {
        return ReservationStatusHistory.builder()
                .reservationId(reservationId)
                .changedByMemberId(changedByMemberId)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .reason(reason)
                .changedAt(changedAt)
                .build();
    }
}
