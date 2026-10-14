package com.ovengers.slotkey.auth.service;

import com.ovengers.slotkey.auth.dto.internal.ChallengeIssueResult;
import com.ovengers.slotkey.auth.entity.VerificationChallenge;
import com.ovengers.slotkey.auth.entity.VerificationGuard;
import com.ovengers.slotkey.auth.entity.VerificationPurpose;
import com.ovengers.slotkey.auth.mail.VerificationMailSender;
import com.ovengers.slotkey.auth.repository.LocalCredentialRepository;
import com.ovengers.slotkey.member.repository.MemberRepository;
import com.ovengers.slotkey.auth.repository.VerificationChallengeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import org.springframework.mail.MailException;
import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class VerificationChallengeService {

    private final VerificationGuardService guardService;
    private final VerificationChallengeRepository challengeRepository;
    private final VerificationSecretGenerator secretGenerator;
    private final VerificationSecretHasher secretHasher;
    private final VerificationMailSender mailSender;
    private final MemberRepository memberRepository;
    private final LocalCredentialRepository localCredentialRepository;
    private final Clock clock;

    @Transactional
    public ChallengeIssueResult issue(
            String email,
            VerificationPurpose purpose
    ) {
        // Guard 준비 및 잠금 확보
        VerificationGuard guard = guardService.acquire(email, purpose);

        // 잠금을 기다렸을 수 있으므로 확보한 뒤 시간을 읽는다.
        LocalDateTime now = LocalDateTime.now(clock);

        // 차단 여부·30초 간격 검사 및 발송 시각 기록
        if (guard.isAdminLocked()) {
            throw new BusinessException(
                    ErrorCode.VERIFICATION_ADMIN_LOCKED
            );
        }

        if (guard.isBlocked(now)) {
            throw new BusinessException(
                    ErrorCode.VERIFICATION_TEMPORARILY_BLOCKED
            );
        }

        if (!guard.canSend(now)) {
            throw new BusinessException(
                    ErrorCode.VERIFICATION_RESEND_TOO_SOON
            );
        }

        guard.recordSent(now);

        // 이전 미인증 번호 무효화
        challengeRepository
                .findAllByGuardIdAndVerifiedAtIsNullAndInvalidatedAtIsNull(
                        guard.getId()
                )
                .forEach(challenge -> challenge.invalidate(now));

        // 새 번호와 보호된 검증값 생성
        String rawCode = secretGenerator.generateCode();
        String codeHash = secretHasher.hashCode(
                guard.getId(),
                rawCode
        );

        VerificationChallenge challenge = new VerificationChallenge(
                guard,
                codeHash,
                now
        );

        // 메일 발송 전에 DB 저장 오류를 먼저 확인
        VerificationChallenge saved =
                challengeRepository.saveAndFlush(challenge);

        // 실패 예외가 발생하면 위 DB 변경도 롤백
        boolean sendMail = purpose == VerificationPurpose.SIGNUP
                || memberRepository.findByEmail(email)
                .map(member -> localCredentialRepository.existsById(member.getId()))
                .orElse(false);

        if (sendMail) {
            try {
                mailSender.sendCode(email, purpose, rawCode);
            } catch (MailException e) {
                throw new BusinessException(
                        ErrorCode.VERIFICATION_MAIL_FAILED
                );
            }
        }

        return new ChallengeIssueResult(
                saved.getId(),
                saved.getExpiresAt(),
                now.plusSeconds(30)
        );
    }
}
