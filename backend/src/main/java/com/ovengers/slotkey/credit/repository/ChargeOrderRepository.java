package com.ovengers.slotkey.credit.repository;

import com.ovengers.slotkey.credit.entity.ChargeOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ChargeOrderRepository extends JpaRepository<ChargeOrder, Long> {

    Optional<ChargeOrder> findByOrderId(String orderId);

    Optional<ChargeOrder> findByMemberIdAndClientIdempotencyKey(
            Long memberId,
            String clientIdempotencyKey
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ChargeOrder c WHERE c.orderId = :orderId")
    Optional<ChargeOrder> findByOrderIdForUpdate(@Param("orderId") String orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ChargeOrder c WHERE c.id = :id")
    Optional<ChargeOrder> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            SELECT c.id
            FROM ChargeOrder c
            WHERE c.status = :status
              AND c.recoveryExhaustedAt IS NULL
              AND (c.recoveryNextAttemptAt IS NULL OR c.recoveryNextAttemptAt <= :now)
              AND c.updatedAt > :confirmingStartedAfter
            ORDER BY c.recoveryNextAttemptAt ASC, c.id ASC
            """)
    List<Long> findRecoveryCandidateIds(
            @Param("status") com.ovengers.slotkey.credit.entity.ChargeOrderStatus status,
            @Param("now") LocalDateTime now,
            @Param("confirmingStartedAfter") LocalDateTime confirmingStartedAfter,
            Pageable pageable
    );

    @Query("""
            SELECT c.id
            FROM ChargeOrder c
            WHERE c.status = :status
              AND c.recoveryExhaustedAt IS NULL
              AND c.updatedAt <= :confirmingStartedAtOrBefore
            ORDER BY c.updatedAt ASC, c.id ASC
            """)
    List<Long> findRecoveryExhaustedCandidateIds(
            @Param("status") com.ovengers.slotkey.credit.entity.ChargeOrderStatus status,
            @Param("confirmingStartedAtOrBefore") LocalDateTime confirmingStartedAtOrBefore,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ChargeOrder c
            SET c.status = :confirming,
                c.paymentKey = :paymentKey,
                c.clientIdempotencyKey = :clientIdempotencyKey,
                c.pgIdempotencyKey = :pgIdempotencyKey,
                c.recoveryNextAttemptAt = :recoveryNextAttemptAt,
                c.recoveryExhaustedAt = NULL,
                c.updatedAt = :now
            WHERE c.id = :id AND c.status = :pending
            """)
    int startConfirming(
            @Param("id") Long id,
            @Param("pending") com.ovengers.slotkey.credit.entity.ChargeOrderStatus pending,
            @Param("confirming") com.ovengers.slotkey.credit.entity.ChargeOrderStatus confirming,
            @Param("paymentKey") String paymentKey,
            @Param("clientIdempotencyKey") String clientIdempotencyKey,
            @Param("pgIdempotencyKey") String pgIdempotencyKey,
            @Param("now") LocalDateTime now,
            @Param("recoveryNextAttemptAt") LocalDateTime recoveryNextAttemptAt
    );
}
