package com.ovengers.slotkey.auth.repository;

import com.ovengers.slotkey.auth.entity.VerificationGrant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VerificationGrantRepository
        extends JpaRepository<VerificationGrant, Long> {

    Optional<VerificationGrant> findByGrantHash(String grantHash);

    List<VerificationGrant>
    findAllByChallenge_Guard_IdAndConsumedAtIsNullAndInvalidatedAtIsNull(
            Long guardId
    );
}