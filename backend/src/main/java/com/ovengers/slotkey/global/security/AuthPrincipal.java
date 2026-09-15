package com.ovengers.slotkey.global.security;

import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.entity.MemberRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;

// 이번 요청을 보낸 회원의 정보를 담는 객체
public record AuthPrincipal(
        Long memberId,
        String email,
        MemberRole role
) {

    public AuthPrincipal(Member member) {
        this(
                member.getId(),
                member.getEmail(),
                member.getRole()
        );
    }

    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(
                new SimpleGrantedAuthority("ROLE_" + role.name())
        );
    }
}