package com.ovengers.slotkey.credit.webhook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.anyLong;

@ExtendWith(MockitoExtension.class)
class PgWebhookEventSchedulerTest {

    private static final int BATCH_SIZE = 50;

    @Mock
    private PgWebhookEventStore eventStore;

    @Mock
    private PgWebhookEventProcessor processor;

    private PgWebhookEventScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new PgWebhookEventScheduler(eventStore, processor, BATCH_SIZE);
    }

    @Test
    @DisplayName("RECEIVED 행을 배치 크기만큼 읽어 한 건씩 처리한다")
    void processReceivedEvents_processesEachReceivedEvent() {
        given(eventStore.findReceivedIds(BATCH_SIZE)).willReturn(List.of(1L, 2L, 3L));

        scheduler.processReceivedEvents();

        verify(processor).process(1L);
        verify(processor).process(2L);
        verify(processor).process(3L);
    }

    @Test
    @DisplayName("한 건의 예외가 뒤 행 처리를 막지 않는다")
    void processReceivedEvents_oneFailure_doesNotStopRemaining() {
        given(eventStore.findReceivedIds(BATCH_SIZE)).willReturn(List.of(1L, 2L));
        doThrow(new IllegalStateException("db down")).when(processor).process(1L);

        scheduler.processReceivedEvents();

        verify(processor).process(2L);
    }

    @Test
    @DisplayName("처리할 행이 없으면 처리기를 호출하지 않는다")
    void processReceivedEvents_empty_doesNothing() {
        given(eventStore.findReceivedIds(BATCH_SIZE)).willReturn(List.of());

        scheduler.processReceivedEvents();

        verify(processor, never()).process(anyLong());
    }
}
