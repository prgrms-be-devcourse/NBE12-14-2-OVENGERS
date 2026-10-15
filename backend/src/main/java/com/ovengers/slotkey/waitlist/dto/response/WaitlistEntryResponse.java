package com.ovengers.slotkey.waitlist.dto.response;

import com.ovengers.slotkey.waitlist.entity.WaitlistEntry;
import com.ovengers.slotkey.waitlist.entity.WaitlistStatus;

import java.time.LocalDateTime;

/**
 * 대기 1건 응답. position은 "같은 공간에서 구간이 겹치는 WAITING 중 나보다 먼저 신청한 건수 + 1"이며
 * 안내용이다 — 자리가 비면 대기자 전원에게 알리고 예약은 선착순이므로 순서가 자리를 보장하지 않는다.
 */
public record WaitlistEntryResponse(
        Long id,
        Long spaceId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        WaitlistStatus status,
        long position,
        LocalDateTime createdAt) {

    public static WaitlistEntryResponse of(WaitlistEntry entry, long position) {
        return new WaitlistEntryResponse(
                entry.getId(),
                entry.getSpaceId(),
                entry.getStartTime(),
                entry.getEndTime(),
                entry.getStatus(),
                position,
                entry.getCreatedAt());
    }
}
