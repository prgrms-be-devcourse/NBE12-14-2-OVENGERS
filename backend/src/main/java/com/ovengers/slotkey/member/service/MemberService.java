package com.ovengers.slotkey.member.service;

import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Member signup(String email, String password, String passwordConfirm, String nickname) {
        // 비밀번호와 비밀번호 확인이 일치하는 지 검증하기
        if (!password.equals(passwordConfirm)) {
            throw new IllegalArgumentException(
                    "비밀번호가 일치하지 않습니다."
            );
        }
        // 이미 가입된 이메일인지 확인하기
        if (memberRepository.existsByEmail(email)) {
            throw new IllegalArgumentException(
                    "이미 사용 중인 이메일입니다."
            );
        }
        // 비밀번호 원문 대신 해시를 저장하기
        String passwordHash = passwordEncoder.encode(password);

        Member member = new Member(email, passwordHash, nickname);

        return memberRepository.save(member);
    }
}