package com.ovengers.slotkey.reservation.entity;

import com.ovengers.slotkey.member.entity.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "reservation_status_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class ReservationStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;              // PK

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;         // 상태가 변경된 예약

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by_member_id")
    private Member changedByMember;          // 상태를 변경한 회원


    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private ReservationStatus fromStatus;     // 변경 전 상태


    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private ReservationStatus toStatus;        // 변경 후 상태

    private String reason;                     // 상태 변경 사유

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;           // 상태 변경 시각

    // 예약 상태 변경 이력 생성
    public ReservationStatusHistory(
            Reservation reservation,
            Member changedByMember,
            ReservationStatus fromStatus,
            ReservationStatus toStatus,
            String reason,
            LocalDateTime changedAt
    )  {
        this.reservation = reservation;
        this.changedByMember = changedByMember;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason;
        this.changedAt = changedAt;
    }
}
