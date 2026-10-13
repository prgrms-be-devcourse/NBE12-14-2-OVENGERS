package com.ovengers.slotkey.auth.repository;

import com.ovengers.slotkey.auth.entity.VerificationGuard;
import com.ovengers.slotkey.auth.entity.VerificationPurpose;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;
import java.util.Optional;

public interface VerificationGuardRepository extends JpaRepository<VerificationGuard, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select g
            from VerificationGuard g
            where g.email = :email
              and g.purpose = :purpose
            """)
    Optional<VerificationGuard> findForUpdate(@Param("email") String email, @Param("purpose") VerificationPurpose purpose);

    @Modifying
    @Query(value = """
        INSERT INTO verification_guard (
            email,
            purpose,
            failed_attempts,
            block_stage,
            admin_locked
        )
        VALUES (
            :email,
            :purpose,
            0,
            0,
            FALSE
        )
        ON DUPLICATE KEY UPDATE id = id
        """, nativeQuery = true)
    void ensureExists(
            @Param("email") String email,
            @Param("purpose") String purpose
    );
}