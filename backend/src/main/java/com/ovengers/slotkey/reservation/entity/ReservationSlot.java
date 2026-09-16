package com.ovengers.slotkey.reservation.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 30분 단위 슬롯 점유 행. 살아있는 점유일 때만 존재하며,
 * UNIQUE(space_id, slot_start)가 동시 예약 차단의 유일한 진실이다(§4-1, §4-2).
 * 취소/노쇼/만료 시 하드 삭제한다.
 */
@Entity
@Table(name = "reservation_slot")
@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ReservationSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reservation_id", nullable = false)
    private Long reservationId;

    @Column(name = "space_id", nullable = false)
    private Long spaceId;

    @Column(name = "slot_start", nullable = false)
    private LocalDateTime slotStart;

    public static ReservationSlot of(Long reservationId, Long spaceId, LocalDateTime slotStart) {
        return ReservationSlot.builder()
                .reservationId(reservationId)
                .spaceId(spaceId)
                .slotStart(slotStart)
                .build();
    }
}
