package com.ovengers.slotkey.audit.dto;

import com.ovengers.slotkey.audit.entity.AuditAction;
import com.ovengers.slotkey.audit.entity.AuditTargetType;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public record AdminAuditLogSearchCondition(
        Long actorMemberId,
        AuditAction action,
        AuditTargetType targetType,
        Long targetId,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate dateFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate dateTo
) {
}
