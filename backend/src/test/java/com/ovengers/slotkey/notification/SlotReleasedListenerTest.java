package com.ovengers.slotkey.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ovengers.slotkey.notification.consumer.SlotReleasedHandler;
import com.ovengers.slotkey.notification.consumer.SlotReleasedListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** 리스너는 payload에서 eventId·reservationId만 꺼내 핸들러에 넘기는 얇은 껍데기다(Kafka 없이 직접 호출). */
class SlotReleasedListenerTest {

    private final SlotReleasedHandler handler = mock(SlotReleasedHandler.class);
    private final SlotReleasedListener listener = new SlotReleasedListener(handler, new ObjectMapper());

    @Test
    @DisplayName("실제 발행 payload(4필드)에서 eventId와 reservationId를 꺼내 핸들러를 호출한다")
    void onMessage_validPayload_callsHandler() {
        listener.onMessage("{\"eventId\":\"e-1\",\"reservationId\":42,\"reason\":\"CANCELLED\","
                + "\"occurredAt\":\"2026-10-16T12:00:00\"}");

        verify(handler).handle("e-1", 42L);
    }

    @Test
    @DisplayName("JSON이 아니거나 필수 필드가 없으면 IllegalArgumentException이고 핸들러를 호출하지 않는다")
    void onMessage_malformedPayload_throwsAndSkipsHandler() {
        assertThatThrownBy(() -> listener.onMessage("not-json")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> listener.onMessage("{\"reservationId\":1}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> listener.onMessage("{\"eventId\":\"e\"}"))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(handler);
    }
}
