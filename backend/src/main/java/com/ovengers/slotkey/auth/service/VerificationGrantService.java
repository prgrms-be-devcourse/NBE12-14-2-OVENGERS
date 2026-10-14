package com.ovengers.slotkey.auth.service;

import com.ovengers.slotkey.auth.entity.VerificationGrant;
import com.ovengers.slotkey.auth.entity.VerificationGuard;
import com.ovengers.slotkey.auth.entity.VerificationPurpose;
import com.ovengers.slotkey.auth.repository.VerificationGrantRepository;
import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class VerificationGrantService {

    private final VerificationGuardService guardService;
    private final VerificationGrantRepository grantRepository;
    private final VerificationSecretHasher secretHasher;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public VerificationGrant requireUsable(
            String email,
            VerificationPurpose purpose,
            String rawGrant
    ) {
        if (rawGrant == null || rawGrant.isBlank()) {
            throw invalidGrant();
        }

        // 발급·확인과 같은 Guard를 잠가 동시 처리를 직렬화한다.
        VerificationGuard guard = guardService.acquire(email, purpose);

        VerificationGrant grant = grantRepository
                .findByGrantHash(secretHasher.hashGrant(rawGrant))
                .orElseThrow(this::invalidGrant);

        // 잠금 대기 중 만료될 수 있으므로 잠금 확보 후 시간을 확인한다.
        LocalDateTime now = LocalDateTime.now(clock);

        // Guard는 이메일·목적 조합을 나타낸다.
        if (!grant.getChallenge().getGuard().getId().equals(guard.getId())
                || !grant.isUsable(now)) {
            throw invalidGrant();
        }

        return grant;
    }

    private BusinessException invalidGrant() {
        return new BusinessException(
                ErrorCode.VERIFICATION_GRANT_INVALID
        );
    }
}