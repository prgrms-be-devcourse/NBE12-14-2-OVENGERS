package com.ovengers.slotkey.member.service;

import com.ovengers.slotkey.auth.entity.LocalCredential;
import com.ovengers.slotkey.auth.repository.LocalCredentialRepository;
import com.ovengers.slotkey.credit.service.CreditGrantService;
import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.member.dto.response.MemberResponse;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ovengers.slotkey.auth.entity.VerificationGrant;
import com.ovengers.slotkey.auth.entity.VerificationPurpose;
import com.ovengers.slotkey.auth.service.VerificationGrantService;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final CreditGrantService creditGrantService;
    private final LocalCredentialRepository localCredentialRepository;
    private final Clock clock;
    private final VerificationGrantService verificationGrantService;
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
            String nickname,
            String rawGrant
    ) {
        // 비밀번호와 확인값 일치 여부 검사
        if (!password.equals(passwordConfirm)) {
            throw new BusinessException(
                    ErrorCode.PASSWORD_CONFIRM_MISMATCH
            );
        }
        // 가입용 증표 검증 및 Guard 잠금 확보
        VerificationGrant grant = verificationGrantService.requireUsable(
                email,
                VerificationPurpose.SIGNUP,
                rawGrant
        );

        LocalDateTime now = LocalDateTime.now(clock);

        // 이메일 중복 검사
        if (memberRepository.existsByEmail(email)) {
            throw new BusinessException(
                    ErrorCode.EMAIL_ALREADY_EXISTS
            );
        }

        // 비밀번호 해시 생성
        String passwordHash = passwordEncoder.encode(password);

        Member member = new Member(email, nickname);

// ① 회원에 이메일 인증 완료 시각 기록
        member.verifyEmail(grant.getChallenge().getVerifiedAt());

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

// ② 비밀번호 정보 생성 시 위에서 구한 now 사용
        LocalCredential localCredential = new LocalCredential(
                savedMember,
                passwordHash,
                now
        );

        localCredentialRepository.save(localCredential);

// 크레딧 지급 중 영속성 컨텍스트가 비워지기 전에 소비
        grant.consume(now);

// flushAutomatically가 위 변경을 DB에 반영한 뒤 clear한다.
        creditGrantService.grantSignupCredit(
                savedMember.getId(),
                signupGrantAmount
        );

// 최신 잔액이 반영된 회원 반환
        return memberRepository.findById(savedMember.getId())
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.MEMBER_NOT_FOUND)
                );
    }

    @Transactional
    public MemberResponse updateNickname(Long memberId, String nickname) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED)
                );

        member.updateNickname(nickname);

        return new MemberResponse(member);
    }
}