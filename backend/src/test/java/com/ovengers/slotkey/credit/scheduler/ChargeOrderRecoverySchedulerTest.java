package com.ovengers.slotkey.credit.scheduler;

import com.ovengers.slotkey.credit.entity.ChargeOrderStatus;
import com.ovengers.slotkey.credit.repository.ChargeOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChargeOrderRecoverySchedulerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 14, 12, 0);
    private static final int BATCH_SIZE = 50;

    @Mock
    private ChargeOrderRepository chargeOrderRepository;

    @Mock
    private ChargeOrderRecoveryProcessor processor;

    private ChargeOrderRecoveryScheduler scheduler;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                NOW.atZone(ZoneId.of("Asia/Seoul")).toInstant(),
                ZoneId.of("Asia/Seoul")
        );
        scheduler = new ChargeOrderRecoveryScheduler(
                chargeOrderRepository,
                processor,
                clock,
                BATCH_SIZE
        );
    }

    @Test
    @DisplayName("한 주기에 24시간 초과 50건과 PG 조회 후보 50건을 각각 처리한다")
    void recoverChargeOrders_usesSeparateLimitsForExhaustedAndLookupCandidates() {
        PageRequest firstFifty = PageRequest.of(0, BATCH_SIZE);
        given(chargeOrderRepository.findRecoveryExhaustedCandidateIds(
                ChargeOrderStatus.CONFIRMING,
                NOW.minusHours(24),
                firstFifty
        )).willReturn(List.of(1L, 2L));
        given(chargeOrderRepository.findRecoveryCandidateIds(
                ChargeOrderStatus.CONFIRMING,
                NOW,
                NOW.minusHours(24),
                firstFifty
        )).willReturn(List.of(3L, 4L));

        scheduler.recoverChargeOrders();

        verify(processor).markExpired(1L, NOW);
        verify(processor).markExpired(2L, NOW);
        verify(processor).recover(3L, NOW);
        verify(processor).recover(4L, NOW);
    }

    @Test
    @DisplayName("한 주문의 복구 예외가 뒤 주문 처리를 막지 않는다")
    void recoverChargeOrders_oneFailure_doesNotStopRemainingOrders() {
        PageRequest firstFifty = PageRequest.of(0, BATCH_SIZE);
        given(chargeOrderRepository.findRecoveryExhaustedCandidateIds(
                ChargeOrderStatus.CONFIRMING,
                NOW.minusHours(24),
                firstFifty
        )).willReturn(List.of());
        given(chargeOrderRepository.findRecoveryCandidateIds(
                ChargeOrderStatus.CONFIRMING,
                NOW,
                NOW.minusHours(24),
                firstFifty
        )).willReturn(List.of(3L, 4L));
        doThrow(new IllegalStateException("first failed"))
                .when(processor).recover(3L, NOW);

        scheduler.recoverChargeOrders();

        verify(processor).recover(3L, NOW);
        verify(processor).recover(4L, NOW);
    }
}
