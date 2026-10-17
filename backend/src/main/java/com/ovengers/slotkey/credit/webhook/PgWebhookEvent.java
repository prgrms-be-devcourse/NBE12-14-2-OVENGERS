package com.ovengers.slotkey.credit.webhook;

/**
 * 웹훅에서 꺼낸 기록용 값. orderId·paymentKey·reportedStatus는 서명 없는 본문의 힌트이며
 * 충전 확정에는 PG 조회 결과만 쓴다.
 */
public record PgWebhookEvent(
        String transmissionId,
        String eventType,
        String orderId,
        String paymentKey,
        String reportedStatus,
        Integer retriedCount
) {
}
