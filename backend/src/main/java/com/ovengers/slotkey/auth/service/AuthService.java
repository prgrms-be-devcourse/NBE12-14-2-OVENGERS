package com.ovengers.slotkey.auth.service;

import com.ovengers.slotkey.global.security.jwt.JwtProvider;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.entity.MemberStatus;
import com.ovengers.slotkey.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    @Transactional(readOnly = true)
    public String login(String email, String password) {
        // 이메일로 회원 조회
        Member member = memberRepository.findByEmail(email).orElseThrow(
                () -> new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다.")
        );

        // 입력한 비밀번호랑 저장된 해시 비교
        if (!passwordEncoder.matches(password, member.getPasswordHash())) {
           throw new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        // MemberStatus가 ACTIVE 인 경우만 허용
        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new IllegalArgumentException("이용이 제한된 계정입니다.");
        }
        return jwtProvider.genAccessToken(member);
    }
}
