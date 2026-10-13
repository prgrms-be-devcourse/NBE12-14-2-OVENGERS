package com.ovengers.slotkey.auth.repository;

import com.ovengers.slotkey.auth.entity.VerificationChallenge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VerificationChallengeRepository
        extends JpaRepository<VerificationChallenge, Long> {

    Optional<VerificationChallenge>
    findFirstByGuardIdOrderByIdDesc(Long guardId);

    List<VerificationChallenge>
    findAllByGuardIdAndVerifiedAtIsNullAndInvalidatedAtIsNull(
            Long guardId
    );
}