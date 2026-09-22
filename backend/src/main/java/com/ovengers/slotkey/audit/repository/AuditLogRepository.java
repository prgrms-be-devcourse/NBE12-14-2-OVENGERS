package com.ovengers.slotkey.audit.repository;

import com.ovengers.slotkey.audit.entity.AuditAction;
import com.ovengers.slotkey.audit.entity.AuditLog;
import com.ovengers.slotkey.audit.entity.AuditTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Optional<AuditLog> findFirstByTargetTypeAndTargetIdAndActionInOrderByCreatedAtDescIdDesc(
            AuditTargetType targetType, Long targetId, Collection<AuditAction> actions);

    @Query(
            value = """
                    SELECT a FROM AuditLog a
                    WHERE (:actorMemberId IS NULL OR a.actorMemberId = :actorMemberId)
                      AND (:action IS NULL OR a.action = :action)
                      AND (:targetType IS NULL OR a.targetType = :targetType)
                      AND (:targetId IS NULL OR a.targetId = :targetId)
                      AND (:fromInclusive IS NULL OR a.createdAt >= :fromInclusive)
                      AND (:toExclusive IS NULL OR a.createdAt < :toExclusive)
                    """,
            countQuery = """
                    SELECT COUNT(a) FROM AuditLog a
                    WHERE (:actorMemberId IS NULL OR a.actorMemberId = :actorMemberId)
                      AND (:action IS NULL OR a.action = :action)
                      AND (:targetType IS NULL OR a.targetType = :targetType)
                      AND (:targetId IS NULL OR a.targetId = :targetId)
                      AND (:fromInclusive IS NULL OR a.createdAt >= :fromInclusive)
                      AND (:toExclusive IS NULL OR a.createdAt < :toExclusive)
                    """
    )
    Page<AuditLog> searchAuditLogs(
            @Param("actorMemberId") Long actorMemberId,
            @Param("action") AuditAction action,
            @Param("targetType") AuditTargetType targetType,
            @Param("targetId") Long targetId,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive,
            Pageable pageable
    );
}
