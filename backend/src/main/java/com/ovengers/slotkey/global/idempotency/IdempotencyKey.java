package com.ovengers.slotkey.global.idempotency;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Idempotency-Key 재요청 시 원래 응답을 그대로 돌려주기 위한 저장 레코드.
 * (idempotency_key, member_id, request_path) 조합이 유일해야 한다 — 같은 키라도
 * 회원이나 엔드포인트가 다르면 서로 다른 요청으로 취급한다.
 */
@Entity
@Table(name = "idempotency_key")
@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String key;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "request_path", nullable = false, length = 255)
    private String requestPath;

    @Column(name = "response_status", nullable = false)
    private int responseStatus;

    @Column(name = "response_body", nullable = false, columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static IdempotencyKey of(String key, Long memberId, String requestPath,
                                     int responseStatus, String responseBody, LocalDateTime now) {
        return IdempotencyKey.builder()
                .key(key)
                .memberId(memberId)
                .requestPath(requestPath)
                .responseStatus(responseStatus)
                .responseBody(responseBody)
                .createdAt(now)
                .build();
    }
}
