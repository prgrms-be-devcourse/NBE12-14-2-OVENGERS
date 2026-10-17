package com.ovengers.slotkey.member.dto.response;

public record AdminMemberDetailResponse(
        AdminMemberResponse member,
        long totalReservationCount,
        long suspensionCount
) {
}