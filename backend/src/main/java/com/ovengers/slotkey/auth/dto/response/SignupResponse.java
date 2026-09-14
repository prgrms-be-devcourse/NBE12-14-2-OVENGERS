package com.ovengers.slotkey.auth.dto.response;

import com.ovengers.slotkey.member.entity.Member;

public record SignupResponse(
        Long memberId,
        String email,
        String nickname
) {

    // 회원 엔티티에서 응답에 필요한 정보만 추출
    public SignupResponse(Member member) {
        this(member.getId(), member.getEmail(), member.getNickname());
    }
}
