package com.ovengers.slotkey.audit.dto;

import com.ovengers.slotkey.audit.entity.AuditAction;
import com.ovengers.slotkey.audit.entity.AuditLog;
import com.ovengers.slotkey.audit.entity.AuditTargetType;

import java.time.LocalDateTime;

public record AuditLogResponse(
        Long id,
        Long actorMemberId,
        AuditAction action,
        AuditTargetType targetType,
        Long targetId,
        String reason,
        String beforeValue,
        String afterValue,
        LocalDateTime createdAt
) {
    public static AuditLogResponse from(AuditLog auditLog) {
        return new AuditLogResponse(
                auditLog.getId(),
                auditLog.getActorMemberId(),
                auditLog.getAction(),
                auditLog.getTargetType(),
                auditLog.getTargetId(),
                auditLog.getReason(),
                auditLog.getBeforeValue(),
                auditLog.getAfterValue(),
                auditLog.getCreatedAt()
        );
    }
}
