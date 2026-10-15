package com.ovengers.slotkey.notification.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * `slot-released` 토픽 리스너. payload(JSON)에서 eventId와 reservationId만 꺼내 핸들러에 넘기는 얇은 껍데기다.
 * `slotkey.kafka.enabled=true`일 때만 빈이 생성되므로, 기본 설정(CI·팀 EC2)에서는 컨테이너도 브로커 연결도 없다.
 *
 * 해석할 수 없는 payload는 IllegalArgumentException을 던진다. 같은 메시지를 다시 읽어도 결과가 같으므로
 * KafkaConsumerConfig가 이 예외를 재시도 대상에서 뺀다.
 */
@Component
@ConditionalOnProperty(name = "slotkey.kafka.enabled", havingValue = "true")
public class SlotReleasedListener {

    static final String TOPIC = "slot-released";
    static final String GROUP_ID = "slotkey-waitlist-notifier";

    private final SlotReleasedHandler handler;
    private final ObjectMapper objectMapper;

    public SlotReleasedListener(SlotReleasedHandler handler, ObjectMapper objectMapper) {
        this.handler = handler;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = TOPIC, groupId = GROUP_ID)
    public void onMessage(String payload) {
        JsonNode node;
        try {
            node = objectMapper.readTree(payload);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unreadable SlotReleased payload", e);
        }
        JsonNode eventId = node.get("eventId");
        JsonNode reservationId = node.get("reservationId");
        if (eventId == null || !eventId.isTextual() || reservationId == null || !reservationId.canConvertToLong()) {
            throw new IllegalArgumentException("SlotReleased payload needs eventId and reservationId");
        }
        handler.handle(eventId.asText(), reservationId.asLong());
    }
}
