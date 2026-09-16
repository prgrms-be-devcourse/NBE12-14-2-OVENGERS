package com.ovengers.slotkey.member.dto.response;

import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.entity.MemberRole;

import java.time.LocalDateTime;

public record MemberResponse(
        String email,
        String nickname,
        MemberRole role,
        LocalDateTime createdAt
) {

    public MemberResponse(Member member) {
        this(
                member.getEmail(),
                member.getNickname(),
                member.getRole(),
                member.getCreatedAt()
        );
    }
}