package com.ovengers.slotkey.auth.service;

import com.ovengers.slotkey.auth.entity.LocalCredential;
import com.ovengers.slotkey.auth.entity.VerificationGrant;
import com.ovengers.slotkey.auth.entity.VerificationPurpose;
import com.ovengers.slotkey.auth.repository.LocalCredentialRepository;
import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final VerificationGrantService verificationGrantService;
    private final MemberRepository memberRepository;
    private final LocalCredentialRepository localCredentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final AuthSessionService authSessionService;

    @Transactional
    public void reset(
            String email,
            String rawGrant,
            String password,
            String passwordConfirm
    ) {
        if (!password.equals(passwordConfirm)) {
            throw new BusinessException(ErrorCode.PASSWORD_CONFIRM_MISMATCH);
        }

        VerificationGrant grant = verificationGrantService.requireUsable(
                email,
                VerificationPurpose.PASSWORD_RESET,
                rawGrant
        );

        Member member = memberRepository.findByEmailForUpdate(email)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.VERIFICATION_GRANT_INVALID
                ));

        LocalCredential credential = localCredentialRepository.findById(member.getId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.VERIFICATION_GRANT_INVALID
                ));

        LocalDateTime now = LocalDateTime.now(clock);
        credential.changePassword(passwordEncoder.encode(password), now);
        grant.consume(now);
        authSessionService.revokeAllForMember(member.getId(), now);
    }
}
