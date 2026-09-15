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
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by_member_id")
    private Member changedByMember;

    // 상태 변경 전
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private ReservationStatus fromStatus;

    // 상태 변경 후
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private ReservationStatus toStatus;

    private String reason;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

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
