package com.ovengers.slotkey.waitlist.service;

import java.time.LocalDateTime;

/**
 * 대기 신청이 예약 도메인에 묻는 두 가지(포트). 구현은 reservation 도메인의 어댑터가 제공한다
 * (space 도메인의 OccupiedSlotProvider와 같은 방향 — 예약 쪽 저장소를 직접 들여다보지 않는다).
 */
public interface ReservationOccupancyProvider {

    /** [startInclusive, endExclusive) 안에 유효 점유 슬롯이 하나라도 있는가. 없으면 대기가 필요 없다. */
    boolean hasOccupiedSlot(Long spaceId, LocalDateTime startInclusive, LocalDateTime endExclusive,
                            LocalDateTime now);

    /**
     * 같은 회원의 유효 점유 예약(CONFIRMED·IN_USE, 또는 만료 전 HELD)이 같은 공간에서
     * 이 구간과 겹치는가(본인 점유자 차단). 만료된 HELD는 점유가 아니므로 now가 필요하다 —
     * 점유의 기준을 예약 경로와 한 군데로 맞추기 위함이다.
     */
    boolean hasOwnOccupyingReservationOverlapping(Long memberId, Long spaceId,
                                                  LocalDateTime startTime, LocalDateTime endTime,
                                                  LocalDateTime now);
}
