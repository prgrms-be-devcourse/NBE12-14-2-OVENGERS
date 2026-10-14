package com.ovengers.slotkey.auth.service;

import com.ovengers.slotkey.auth.dto.internal.VerificationCheckResult;
import com.ovengers.slotkey.auth.entity.*;
import com.ovengers.slotkey.audit.entity.AuditAction;
import com.ovengers.slotkey.audit.entity.AuditTargetType;
import com.ovengers.slotkey.audit.service.AuditLogService;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.entity.MemberStatus;
import com.ovengers.slotkey.member.repository.MemberRepository;
import com.ovengers.slotkey.auth.repository.VerificationChallengeRepository;
import com.ovengers.slotkey.auth.repository.VerificationGrantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

import static com.ovengers.slotkey.auth.dto.internal
        .VerificationCheckResult.Status.*;

@Service
@RequiredArgsConstructor
public class VerificationCheckService {

    private final VerificationGuardService guardService;
    private final VerificationChallengeRepository challengeRepository;
    private final VerificationGrantRepository grantRepository;
    private final VerificationSecretGenerator secretGenerator;
    private final VerificationSecretHasher secretHasher;
    private final Clock clock;
    private final MemberRepository memberRepository;
    private final AuditLogService auditLogService;

    @Transactional
    public VerificationCheckResult verify(
            String email,
            VerificationPurpose purpose,
            Long challengeId,
            String rawCode
    ) {
        VerificationGuard guard = guardService.acquire(email, purpose);
        LocalDateTime now = LocalDateTime.now(clock);

        if (guard.isBlocked(now)) {
            return blockedResult(guard);
        }

        guard.prepareAttempt(now);

        VerificationChallenge challenge = challengeRepository
                .findFirstByGuardIdOrderByIdDesc(guard.getId())
                .orElse(null);

        // 최신 발급 건만 확인한다.
        if (challenge == null
                || !challenge.getId().equals(challengeId)
                || !challenge.isUsable(now)) {
            return VerificationCheckResult.failure(
                    INVALID_CHALLENGE, null
            );
        }

        if (!secretHasher.matchesCode(
                guard.getId(), rawCode, challenge.getCodeHash()
        )) {
            boolean blocked = guard.recordFailure(now);

            if (blocked) {
                challenge.invalidate(now);
                suspendMemberIfNeeded(email);
                return blockedResult(guard);
            }

            return VerificationCheckResult.failure(
                    WRONG_CODE, null
            );
        }

        challenge.markVerified(now);
        guard.recordSuccess(now);

        String rawGrant = secretGenerator.generateGrant();

        VerificationGrant grant = grantRepository.saveAndFlush(
                new VerificationGrant(
                        challenge,
                        secretHasher.hashGrant(rawGrant),
                        now
                )
        );

        return new VerificationCheckResult(
                SUCCESS,
                rawGrant,
                grant.getExpiresAt(),
                null
        );
    }

    private void suspendMemberIfNeeded(String email) {
        Member member = memberRepository.findByEmailForUpdate(email)
                .orElse(null);

        if (member == null || member.getStatus() != MemberStatus.ACTIVE) {
            return;
        }

        MemberStatus beforeStatus = member.getStatus();
        member.updateStatus(MemberStatus.SUSPENDED);
        auditLogService.log(
                null,
                AuditAction.LOCK_MEMBER_BY_VERIFICATION,
                AuditTargetType.MEMBER,
                member.getId(),
                "이메일 인증 실패 3단계 초과",
                beforeStatus,
                MemberStatus.SUSPENDED
        );
    }

    private VerificationCheckResult blockedResult(
            VerificationGuard guard
    ) {
        return VerificationCheckResult.failure(
                guard.isAdminLocked()
                        ? ADMIN_LOCKED
                        : TEMPORARILY_BLOCKED,
                guard.getBlockedUntil()
        );
    }
}
