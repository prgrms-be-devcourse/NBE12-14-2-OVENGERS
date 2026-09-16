package com.ovengers.slotkey.reservation.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * 결제 확인(pay) 요청. spaceVersion은 HOLD 생성 응답에서 받은 값을 그대로 되돌려 보낸다.
 * 비교 기준이 가격 값이 아니라 version인 이유는 ABA 문제 때문이다(§5-2).
 * Idempotency-Key는 바디가 아니라 헤더로 받는다(api-명세서.md 1-7).
 */
public record ReservationPayRequest(
        @NotNull Integer spaceVersion
) {
}
