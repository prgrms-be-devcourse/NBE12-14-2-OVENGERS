package com.ovengers.slotkey.member.dto.response;

import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.entity.MemberRole;
import com.ovengers.slotkey.member.entity.MemberStatus;

import java.time.LocalDateTime;

public record AdminMemberResponse(
        Long memberId,
        String email,
        String nickname,
        MemberRole role,
        MemberStatus status,
        int balance,
        LocalDateTime createdAt,
        LocalDateTime lastStatusChangedAt,
        String lastStatusChangeReason
) {

    public static AdminMemberResponse of(Member member, LocalDateTime lastStatusChangedAt, String lastStatusChangeReason) {
        return new AdminMemberResponse(
                member.getId(),
                member.getEmail(),
                member.getNickname(),
                member.getRole(),
                member.getStatus(),
                member.getBalance(),
                member.getCreatedAt(),
                lastStatusChangedAt,
                lastStatusChangeReason
        );
    }
}
