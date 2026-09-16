package com.ovengers.slotkey.member.service;

import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
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

    // 내 정보 조회
    @Transactional(readOnly = true)
    public Member getMyInfo(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED)
                );
    }

    @Transactional
    public Member signup(
            String email,
            String password,
            String passwordConfirm,
            String nickname
    ) {
        // 비밀번호와 확인값 일치 여부 검사
        if (!password.equals(passwordConfirm)) {
            throw new BusinessException(
                    ErrorCode.PASSWORD_CONFIRM_MISMATCH
            );
        }

        // 이메일 중복 검사
        if (memberRepository.existsByEmail(email)) {
            throw new BusinessException(
                    ErrorCode.EMAIL_ALREADY_EXISTS
            );
        }

        // 비밀번호 해시 생성 후 회원 저장
        String passwordHash = passwordEncoder.encode(password);

        Member member = new Member(email, passwordHash, nickname);

        return memberRepository.save(member);
    }
}