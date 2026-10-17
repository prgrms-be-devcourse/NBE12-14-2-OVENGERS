package com.ovengers.slotkey.credit.webhook;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 토스페이먼츠가 호출하는 웹훅 주소. JWT를 붙일 수 없어 SecurityConfig에서 이 POST 하나만 인증 없이 연다.
 * 본문은 믿지 않고 기록만 한 뒤 바로 200을 준다(10초 안에 200 응답 규칙).
 */
@Tag(name = "PG 웹훅", description = "토스페이먼츠 웹훅 수신")
@RestController
@RequestMapping("/api/v1/webhooks/tosspayments")
public class TossWebhookController {

    private final TossWebhookReceiver receiver;

    public TossWebhookController(TossWebhookReceiver receiver) {
        this.receiver = receiver;
    }

    @Operation(
            summary = "토스페이먼츠 웹훅 수신",
            description = "PAYMENT_STATUS_CHANGED를 즉시 기록하고 200을 반환합니다. 충전 확정은 별도 처리에서 PG 조회로만 합니다."
    )
    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestHeader(value = "tosspayments-webhook-transmission-id", required = false)
            String transmissionId,
            @RequestHeader(value = "tosspayments-webhook-transmission-retried-count", required = false)
            String retriedCount,
            @RequestBody(required = false) String body
    ) {
        receiver.receive(transmissionId, retriedCount, body);
        return ResponseEntity.ok().build();
    }
}
