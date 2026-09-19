package com.ovengers.slotkey.member.dto.response;

import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.entity.MemberRole;

import java.time.LocalDateTime;

public record MemberResponse(
        Long memberId,
        String email,
        String nickname,
        MemberRole role,
        int balance,
        LocalDateTime createdAt
) {

    public MemberResponse(Member member) {
        this(

                member.getId(),
                member.getEmail(),
                member.getNickname(),
                member.getRole(),
                member.getBalance(),
                member.getCreatedAt()
        );
    }
}