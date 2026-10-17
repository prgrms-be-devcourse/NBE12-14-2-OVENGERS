package com.ovengers.slotkey.credit.webhook;

/**
 * 웹훅 행 처리 결과(pg_webhook_event.result). docs/decisions/pg-charge.md 5단계 결정 4-2.
 * CONFIRMED만 이 처리의 DB 2 호출이 주문을 PAID로 전이시킨 경우다(주문 잠금 아래에서 판정).
 * ALREADY_PAID는 처리 시점 또는 잠금을 얻은 시점에 이미 같은 paymentKey로 PAID였던 경우다.
 * 나머지는 주문을 바꾸지 않은 경우다.
 */
public enum PgWebhookEventResult {
    CONFIRMED,
    ALREADY_PAID,
    ORDER_PENDING,
    ORDER_FAILED,
    ORDER_NOT_FOUND,
    ORDER_INVARIANT_VIOLATION,
    PG_NOT_APPROVED,
    PG_MISMATCH,
    PG_LOOKUP_ERROR,
    STATE_CONFLICT,
    COMPLETE_ERROR
}
