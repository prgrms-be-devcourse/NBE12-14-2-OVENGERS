package com.ovengers.slotkey.auth.service;

import com.ovengers.slotkey.auth.entity.VerificationGuard;
import com.ovengers.slotkey.auth.entity.VerificationPurpose;
import com.ovengers.slotkey.auth.repository.VerificationGuardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VerificationGuardService {

    private final VerificationGuardRepository guardRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public VerificationGuard acquire(
            String email,
            VerificationPurpose purpose
    ) {
        guardRepository.ensureExists(email, purpose.name());

        return guardRepository.findForUpdate(email, purpose)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "인증 상태 생성에 실패했습니다."
                        )
                );
    }
}