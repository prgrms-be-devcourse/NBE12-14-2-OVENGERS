package com.ovengers.slotkey.auth.service;

import com.ovengers.slotkey.auth.entity.AuthSession;
import com.ovengers.slotkey.auth.repository.AuthSessionRepository;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.entity.MemberStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataAccessException;
import jakarta.persistence.PersistenceException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.TransactionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthSessionService {

    private static final Logger log = LoggerFactory.getLogger(AuthSessionService.class);

    private final AuthSessionRepository authSessionRepository;
    private final Clock clock;

    @Transactional
    public AuthSession create(Member member) {
        return authSessionRepository.save(
                new AuthSession(member, LocalDateTime.now(clock))
        );
    }

    public ValidationResult validate(String sessionId, Long memberId) {
        if (sessionId == null || sessionId.isBlank()) {
            return ValidationResult.INVALID;
        }
        try {
            return authSessionRepository.isUsable(
                    sessionId,
                    memberId,
                    LocalDateTime.now(clock),
                    MemberStatus.ACTIVE
            ) ? ValidationResult.VALID : ValidationResult.INVALID;
        } catch (DataAccessException | PersistenceException | TransactionException failure) {
            log.error("Authentication session lookup failed; rejecting request (causeType={})",
                    failure.getClass().getSimpleName());
            return ValidationResult.UNAVAILABLE;
        }
    }

    @Transactional
    public void revoke(AuthSession session, LocalDateTime now) {
        session.revoke(now);
    }

    public enum ValidationResult {
        VALID,
        INVALID,
        UNAVAILABLE
    }
}
