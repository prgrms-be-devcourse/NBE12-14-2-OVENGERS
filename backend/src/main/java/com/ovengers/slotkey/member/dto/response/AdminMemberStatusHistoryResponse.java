
package com.ovengers.slotkey.member.dto.response;

import com.ovengers.slotkey.audit.entity.AuditAction;
import com.ovengers.slotkey.audit.entity.AuditLog;

import java.time.LocalDateTime;

public record AdminMemberStatusHistoryResponse(
        Long historyId,
        AuditAction action,
        Long actorMemberId,
        String reason,
        String beforeValue,
        String afterValue,
        LocalDateTime changedAt
) {

    public static AdminMemberStatusHistoryResponse from(AuditLog auditLog) {
        return new AdminMemberStatusHistoryResponse(
                auditLog.getId(),
                auditLog.getAction(),
                auditLog.getActorMemberId(),
                auditLog.getReason(),
                auditLog.getBeforeValue(),
                auditLog.getAfterValue(),
                auditLog.getCreatedAt()
        );
    }
}
