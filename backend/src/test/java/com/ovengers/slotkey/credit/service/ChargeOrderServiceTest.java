package com.ovengers.slotkey.credit.service;

import com.ovengers.slotkey.credit.dto.response.ChargeOrderResponse;
import com.ovengers.slotkey.credit.entity.ChargeOrder;
import com.ovengers.slotkey.credit.entity.ChargeOrderStatus;
import com.ovengers.slotkey.credit.repository.ChargeOrderRepository;
import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.entity.MemberStatus;
import com.ovengers.slotkey.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChargeOrderServiceTest {

    @Mock
    private ChargeOrderRepository chargeOrderRepository;

    @Mock
    private MemberRepository memberRepository;

    private ChargeOrderService chargeOrderService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-10-13T00:00:00Z"),
                ZoneId.of("Asia/Seoul")
        );
        chargeOrderService = new ChargeOrderService(
                chargeOrderRepository,
                memberRepository,
                clock
        );
    }

    @ParameterizedTest
    @ValueSource(longs = {1_000L, 1_100L, 100_000L})
    @DisplayName("허용된 충전 금액이면 PENDING 주문을 저장한다")
    void create_validAmount_savesPendingOrder(long amount) {
        given(memberRepository.findById(7L)).willReturn(Optional.of(activeMember()));
        given(chargeOrderRepository.save(any(ChargeOrder.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        ChargeOrderResponse response = chargeOrderService.create(7L, amount);

        ArgumentCaptor<ChargeOrder> captor = ArgumentCaptor.forClass(ChargeOrder.class);
        verify(chargeOrderRepository).save(captor.capture());
        ChargeOrder saved = captor.getValue();

        assertThat(saved.getMemberId()).isEqualTo(7L);
        assertThat(saved.getAmount()).isEqualTo(amount);
        assertThat(saved.getStatus()).isEqualTo(ChargeOrderStatus.PENDING);
        assertThat(response.amount()).isEqualTo(amount);
        assertThat(response.status()).isEqualTo(ChargeOrderStatus.PENDING);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1_000L, 999L, 1_001L, 100_100L})
    @DisplayName("최소·최대·100원 단위 규칙을 벗어난 충전 금액은 거절한다")
    void create_invalidAmount_throwsValidationFailed(long amount) {
        assertThatThrownBy(() -> chargeOrderService.create(7L, amount))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);

        verify(chargeOrderRepository, never()).save(any(ChargeOrder.class));
    }

    @ParameterizedTest
    @ValueSource(longs = {1_000L, 100_000L})
    @DisplayName("생성된 orderId는 chg_와 소문자 UUID v4 형식이고 6자 이상 64자 이하이다")
    void create_generatesCoreApiCompatibleOrderId(long amount) {
        given(memberRepository.findById(7L)).willReturn(Optional.of(activeMember()));
        given(chargeOrderRepository.save(any(ChargeOrder.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        ChargeOrderResponse response = chargeOrderService.create(7L, amount);

        String orderId = response.orderId();
        assertThat(orderId).matches("^chg_[0-9a-f-]{36}$");
        assertThat(orderId.length()).isBetween(6, 64);
        assertThat(UUID.fromString(orderId.substring(4)).version()).isEqualTo(4);
    }

    @Test
    @DisplayName("정지 회원은 충전 주문을 생성할 수 없다")
    void create_suspendedMember_throwsAccountInactive() {
        Member member = activeMember();
        member.updateStatus(MemberStatus.SUSPENDED);
        given(memberRepository.findById(7L)).willReturn(Optional.of(member));

        assertThatThrownBy(() -> chargeOrderService.create(7L, 1_000L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCOUNT_INACTIVE);

        verify(chargeOrderRepository, never()).save(any(ChargeOrder.class));
    }

    private Member activeMember() {
        return new Member("member@test.com", "passwordHash", "member");
    }
}
