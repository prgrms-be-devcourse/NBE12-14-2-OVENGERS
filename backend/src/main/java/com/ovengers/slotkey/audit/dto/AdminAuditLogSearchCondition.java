package com.ovengers.slotkey.audit.dto;

import com.ovengers.slotkey.audit.entity.AuditAction;
import com.ovengers.slotkey.audit.entity.AuditTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Schema(description = "감사 로그 검색 조건. 모든 조건은 선택 사항입니다.")
public record AdminAuditLogSearchCondition(

        @Schema(description = "작업을 수행한 관리자 회원 ID", example = "1")
        Long actorMemberId,

        @Schema(description = "관리자가 수행한 작업 종류")
        AuditAction action,

        @Schema(description = "작업 대상의 종류")
        AuditTargetType targetType,

        @Schema(description = "작업 대상 ID. 대상 종류와 함께 지정하면 구분이 명확해집니다.", example = "10")
        Long targetId,

        @Schema(description = "조회 시작 날짜 (포함)", example = "2026-09-01")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate dateFrom,

        @Schema(description = "조회 종료 날짜 (해당 날짜 전체 포함)", example = "2026-09-30")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate dateTo
) {
}