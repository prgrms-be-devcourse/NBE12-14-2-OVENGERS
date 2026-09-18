package com.ovengers.slotkey.member.dto.response;

import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.entity.MemberStatus;

import java.time.LocalDateTime;

public record MemberStatusChangeResponse(
        Long memberId,
        MemberStatus status,
        String reason,
        LocalDateTime changedAt
) {

    public static MemberStatusChangeResponse of(Member member, String reason, LocalDateTime changedAt) {
        return new MemberStatusChangeResponse(member.getId(), member.getStatus(), reason, changedAt);
    }
}
