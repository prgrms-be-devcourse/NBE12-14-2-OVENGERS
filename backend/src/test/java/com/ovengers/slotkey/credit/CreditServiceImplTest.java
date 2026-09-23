package com.ovengers.slotkey.credit;

import com.ovengers.slotkey.credit.entity.CreditTransaction;
import com.ovengers.slotkey.credit.entity.CreditTransactionType;
import com.ovengers.slotkey.credit.repository.CreditBalanceRepository;
import com.ovengers.slotkey.credit.repository.CreditTransactionRepository;
import com.ovengers.slotkey.credit.service.CreditServiceImpl;
import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.repository.MemberRepository;
import com.ovengers.slotkey.reservation.entity.Reservation;
import com.ovengers.slotkey.reservation.repository.ReservationRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreditServiceImplTest {

    @Mock
    private CreditTransactionRepository creditTransactionRepository;

    @Mock
    private CreditBalanceRepository creditBalanceRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ReservationRepository reservationRepository;

    private Clock clock;
    private CreditServiceImpl creditService;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(
                Instant.parse("2026-09-17T00:00:00Z"),
                ZoneId.of("Asia/Seoul")
        );

        creditService = new CreditServiceImpl(
                creditTransactionRepository,
                creditBalanceRepository,
                memberRepository,
                reservationRepository,
                clock
        );
    }

    // 결제 성공
    @Test
    @DisplayName("결제 성공 시 크레딧을 차감하고 거래 내역을 저장한다")
    void charge_success() {
        // given
        Long memberId = 1L;
        Long reservationId = 10L;
        int amount = 3000;

        Member member = mock(Member.class);
        Reservation reservation = mock(Reservation.class);

        given(creditBalanceRepository.decreaseIfEnough(memberId, amount))
                .willReturn(1);
        given(memberRepository.findById(memberId))
                .willReturn(Optional.of(member));
        given(reservationRepository.findById(reservationId))
                .willReturn(Optional.of(reservation));
        given(member.getBalance())
                .willReturn(7000);

        // when
        int balance = creditService.charge(
                memberId,
                reservationId,
                amount
        );

        // then
        assertThat(balance).isEqualTo(7000);

        verify(creditBalanceRepository)
                .decreaseIfEnough(memberId, amount);

        ArgumentCaptor<CreditTransaction> captor =
                ArgumentCaptor.forClass(CreditTransaction.class);

        verify(creditTransactionRepository)
                .save(captor.capture());

        CreditTransaction saved = captor.getValue();

        assertThat(saved.getAmount()).isEqualTo(-3000);
        assertThat(saved.getType())
                .isEqualTo(CreditTransactionType.RESERVATION_CHARGE);
        assertThat(saved.getBalanceAfter()).isEqualTo(7000);
    }

    // 환급 성공
    @Test
    @DisplayName("환급 성공 시 크레딧을 증가시키고 거래 내역을 저장한다")
    void refund_success() {
        // given
        Long memberId = 1L;
        Long reservationId = 10L;
        int amount = 3000;

        Member member = mock(Member.class);
        Reservation reservation = mock(Reservation.class);

        given(creditBalanceRepository.increase(memberId, amount))
                .willReturn(1);
        given(memberRepository.findById(memberId))
                .willReturn(Optional.of(member));
        given(reservationRepository.findById(reservationId))
                .willReturn(Optional.of(reservation));
        given(member.getBalance())
                .willReturn(
                        10000,
                        13000
                );

        // when
        int balance = creditService.refund(
                memberId,
                reservationId,
                amount
        );

        // then
        assertThat(balance).isEqualTo(13000);

        verify(creditBalanceRepository)
                .increase(memberId, amount);

        ArgumentCaptor<CreditTransaction> captor =
                ArgumentCaptor.forClass(CreditTransaction.class);

        verify(creditTransactionRepository)
                .save(captor.capture());

        CreditTransaction saved = captor.getValue();

        assertThat(saved.getAmount()).isEqualTo(3000);
        assertThat(saved.getType())
                .isEqualTo(CreditTransactionType.REFUND);
        assertThat(saved.getBalanceAfter()).isEqualTo(13000);
    }

    // 위약금 성공
    @Test
    @DisplayName("위약금 차감 성공 시 크레딧을 차감하고 거래 내역을 저장한다")
    void penalize_success() {
        // given
        Long memberId = 1L;
        Long reservationId = 10L;
        int amount = 1500;

        Member member = mock(Member.class);
        Reservation reservation = mock(Reservation.class);

        given(creditBalanceRepository.decreaseIfEnough(memberId, amount))
                .willReturn(1);
        given(memberRepository.findById(memberId))
                .willReturn(Optional.of(member));
        given(reservationRepository.findById(reservationId))
                .willReturn(Optional.of(reservation));
        given(member.getBalance())
                .willReturn(8500);

        // when
        int balance = creditService.penalize(
                memberId,
                reservationId,
                amount
        );

        // then
        assertThat(balance).isEqualTo(8500);

        verify(creditBalanceRepository)
                .decreaseIfEnough(memberId, amount);

        ArgumentCaptor<CreditTransaction> captor =
                ArgumentCaptor.forClass(CreditTransaction.class);

        verify(creditTransactionRepository)
                .save(captor.capture());

        CreditTransaction saved = captor.getValue();

        assertThat(saved.getAmount()).isEqualTo(-1500);
        assertThat(saved.getType())
                .isEqualTo(CreditTransactionType.PENALTY);
        assertThat(saved.getBalanceAfter()).isEqualTo(8500);
    }

    // 결제 잔액 부족
    @Test
    @DisplayName("결제 시 잔액이 부족하면 INSUFFICIENT_BALANCE 예외가 발생한다")
    void charge_insufficientBalance() {
        // given
        Long memberId = 1L;
        Long reservationId = 10L;
        int amount = 3000;

        given(creditBalanceRepository.decreaseIfEnough(memberId, amount))
                .willReturn(0);

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> creditService.charge(
                        memberId,
                        reservationId,
                        amount
                )
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.INSUFFICIENT_BALANCE);

        verify(creditTransactionRepository, never())
                .save(any(CreditTransaction.class));
    }

    // 위약금 잔액 부족
    @Test
    @DisplayName("위약금 차감 시 잔액이 부족하면 INSUFFICIENT_BALANCE 예외가 발생한다")
    void penalize_insufficientBalance() {
        // given
        Long memberId = 1L;
        Long reservationId = 10L;
        int amount = 1500;

        given(creditBalanceRepository.decreaseIfEnough(memberId, amount))
                .willReturn(0);

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> creditService.penalize(
                        memberId,
                        reservationId,
                        amount
                )
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.INSUFFICIENT_BALANCE);

        verify(creditTransactionRepository, never())
                .save(any(CreditTransaction.class));
    }

    // 잘못된 금액
    @ParameterizedTest
    @ValueSource(ints = {0, -1000})
    @DisplayName("0 이하의 금액은 모든 크레딧 처리를 거절한다")
    void invalidAmount_throwsException(int amount) {
        // given
        Long memberId = 1L;
        Long reservationId = 10L;

        // when
        BusinessException chargeException = assertThrows(
                BusinessException.class,
                () -> creditService.charge(
                        memberId,
                        reservationId,
                        amount
                )
        );

        BusinessException refundException = assertThrows(
                BusinessException.class,
                () -> creditService.refund(
                        memberId,
                        reservationId,
                        amount
                )
        );

        BusinessException penalizeException = assertThrows(
                BusinessException.class,
                () -> creditService.penalize(
                        memberId,
                        reservationId,
                        amount
                )
        );

        // then
        assertThat(chargeException.getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(refundException.getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(penalizeException.getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);

        verifyNoInteractions(creditBalanceRepository);
    }

    // 환급 대상 회원 없음
    @Test
    @DisplayName("환급 대상 회원이 없으면 MEMBER_NOT_FOUND 예외가 발생한다")
    void refund_memberNotFound() {
        // given
        Long memberId = 1L;
        Long reservationId = 10L;
        int amount = 3000;

        given(memberRepository.findById(memberId))
                .willReturn(Optional.empty());

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> creditService.refund(
                        memberId,
                        reservationId,
                        amount
                )
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);

        verify(creditBalanceRepository, never())
                .increase(memberId, amount);

        verify(creditTransactionRepository, never())
                .save(any(CreditTransaction.class));
    }

    // 환불 후 잔액 최대값 초과
    @Test
    @DisplayName("환불 후 잔액이 int 최대값을 초과하면 환불을 거절한다")
    void refund_balanceOverflow_throwsException() {
        // given
        Long memberId = 1L;
        Long reservationId = 10L;
        int amount = 1000;

        Member member = mock(Member.class);

        given(memberRepository.findById(memberId))
                .willReturn(Optional.of(member));
        given(member.getBalance())
                .willReturn(Integer.MAX_VALUE - 500);

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> creditService.refund(
                        memberId,
                        reservationId,
                        amount
                )
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);

        verify(creditBalanceRepository, never())
                .increase(memberId, amount);

        verify(creditTransactionRepository, never())
                .save(any(CreditTransaction.class));
    }

    // 예약 조회 실패
    @Test
    @DisplayName("예약을 찾을 수 없으면 RESERVATION_NOT_FOUND 예외가 발생한다")
    void reservationNotFound() {
        // given
        Long memberId = 1L;
        Long reservationId = 10L;
        int amount = 3000;

        Member member = mock(Member.class);

        given(creditBalanceRepository.decreaseIfEnough(memberId, amount))
                .willReturn(1);
        given(memberRepository.findById(memberId))
                .willReturn(Optional.of(member));
        given(reservationRepository.findById(reservationId))
                .willReturn(Optional.empty());

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> creditService.charge(
                        memberId,
                        reservationId,
                        amount
                )
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.RESERVATION_NOT_FOUND);

        verify(creditTransactionRepository, never())
                .save(any(CreditTransaction.class));
    }
}