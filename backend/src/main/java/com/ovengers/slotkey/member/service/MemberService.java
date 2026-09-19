package com.ovengers.slotkey.member.service;


import com.ovengers.slotkey.credit.service.CreditGrantService;
import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final CreditGrantService creditGrantService;

    @Value("${app.credit.signup-grant}")
    private int signupGrantAmount;

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


        // 비밀번호 해시 생성
        String passwordHash = passwordEncoder.encode(password);

        Member member = new Member(email, passwordHash, nickname);


        Member savedMember;

        // 회원 저장 + DB UNIQUE 제약에 의한 이메일 중복 처리
        try {
            savedMember = memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {
            if (e.getCause() instanceof ConstraintViolationException violation) {
                String name = violation.getConstraintName();

                if ("email".equals(name) || "member.email".equals(name)) {
                    throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
                }
            }

            throw e;
        }


        // 회원가입 초기 크레딧 지급
        creditGrantService.grantSignupCredit(
                savedMember.getId(),
                signupGrantAmount
        );

        // 크레딧 지급 후 최신 잔액이 반영된 회원 반환
        return memberRepository.findById(savedMember.getId())
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.MEMBER_NOT_FOUND)
                );
    }
}