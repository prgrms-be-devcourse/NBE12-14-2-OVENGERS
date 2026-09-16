package com.ovengers.slotkey.reservation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 관리자 강제 취소 요청. reason은 audit_log에 그대로 기록된다(api-명세서 6-3). */
public record ForceCancelRequest(
        @NotBlank @Size(max = 500) String reason
) {
}
