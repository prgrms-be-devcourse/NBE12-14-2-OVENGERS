package com.ovengers.slotkey.audit.controller;

import com.ovengers.slotkey.audit.dto.AdminAuditLogSearchCondition;
import com.ovengers.slotkey.audit.dto.AuditLogResponse;
import com.ovengers.slotkey.audit.service.AuditLogQueryService;
import com.ovengers.slotkey.global.common.response.ApiResponse;
import com.ovengers.slotkey.global.common.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@RequiredArgsConstructor
public class AdminAuditLogController {

    private final AuditLogQueryService auditLogQueryService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AuditLogResponse>>> getAuditLogs(
            @Valid @ModelAttribute AdminAuditLogSearchCondition condition,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        PageResponse<AuditLogResponse> response =
                PageResponse.from(auditLogQueryService.searchAuditLogs(condition, pageable));
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
