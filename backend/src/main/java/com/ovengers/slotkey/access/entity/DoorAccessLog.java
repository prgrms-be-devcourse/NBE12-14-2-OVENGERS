package com.ovengers.slotkey.access.entity;

import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.reservation.entity.Reservation;
import com.ovengers.slotkey.space.entity.Space;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "door_access_log")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DoorAccessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_member_id")
    private Member actorMember;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id")
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_space_id")
    private Space requestedSpace;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccessResult result;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", length = 50)
    private AccessDenyReason reasonCode;

    @Column(name = "attempted_at", nullable = false)
    private LocalDateTime attemptedAt;

    // 출입 시도 결과 로그 생성
    public DoorAccessLog(
            Member actorMember,
            Reservation reservation,
            Space requestedSpace,
            AccessResult result,
            AccessDenyReason reasonCode,
            LocalDateTime attemptedAt
    ) {
        this.actorMember = actorMember;
        this.reservation = reservation;
        this.requestedSpace = requestedSpace;
        this.result = result;
        this.reasonCode = reasonCode;
        this.attemptedAt = attemptedAt;
    }
}
