package com.ovengers.slotkey.audit.repository;

import com.ovengers.slotkey.audit.entity.AuditAction;
import com.ovengers.slotkey.audit.entity.AuditLog;
import com.ovengers.slotkey.audit.entity.AuditTargetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Optional<AuditLog> findFirstByTargetTypeAndTargetIdAndActionInOrderByCreatedAtDesc(
            AuditTargetType targetType, Long targetId, Collection<AuditAction> actions);
}
