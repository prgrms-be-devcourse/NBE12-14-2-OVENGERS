package com.ovengers.slotkey.credit;

import com.ovengers.slotkey.credit.entity.CreditTransaction;
import com.ovengers.slotkey.credit.entity.CreditTransactionType;
import com.ovengers.slotkey.credit.repository.CreditBalanceRepository;
import com.ovengers.slotkey.credit.repository.CreditTransactionRepository;
import com.ovengers.slotkey.credit.service.CreditGrantService;
import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
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
class CreditGrantServiceTest {

    @Mock
    private CreditBalanceRepository creditBalanceRepository;

    @Mock
    private CreditTransactionRepository creditTransactionRepository;

    @Mock
    private MemberRepository memberRepository;

    private Clock clock;
    private CreditGrantService creditGrantService;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(
                Instant.parse("2026-09-17T00:00:00Z"),
                ZoneId.of("Asia/Seoul")
        );

        creditGrantService = new CreditGrantService(
                creditBalanceRepository,
                creditTransactionRepository,
                memberRepository,
                clock
        );
    }

    // 회원가입 지급 성공
    @Test
    @DisplayName("회원가입 크레딧 지급 성공 시 거래 내역을 저장한다")
    void grantSignupCredit_success() {
        // given
        Long memberId = 1L;
        int amount = 10000;

        Member member = mock(Member.class);

        given(creditBalanceRepository.increase(memberId, amount))
                .willReturn(1);
        given(memberRepository.findById(memberId))
                .willReturn(Optional.of(member));
        given(member.getBalance())
                .willReturn(10000);

        // when
        int balance = creditGrantService.grantSignupCredit(
                memberId,
                amount
        );

        // then
        assertThat(balance).isEqualTo(10000);

        ArgumentCaptor<CreditTransaction> captor =
                ArgumentCaptor.forClass(CreditTransaction.class);

        verify(creditTransactionRepository)
                .save(captor.capture());

        CreditTransaction saved = captor.getValue();

        assertThat(saved.getAmount()).isEqualTo(10000);
        assertThat(saved.getType())
                .isEqualTo(CreditTransactionType.SIGNUP_GRANT);
        assertThat(saved.getBalanceAfter()).isEqualTo(10000);
        assertThat(saved.getReservation()).isNull();
        assertThat(saved.getReason()).isNull();
    }

    // 관리자 지급 성공
    @Test
    @DisplayName("관리자 크레딧 지급 성공 시 지급 사유와 거래 내역을 저장한다")
    void grantAdminCredit_success() {
        // given
        Long memberId = 1L;
        int amount = 5000;
        String reason = "이벤트 보상";

        Member member = mock(Member.class);

        given(creditBalanceRepository.increase(memberId, amount))
                .willReturn(1);
        given(memberRepository.findById(memberId))
                .willReturn(Optional.of(member));
        given(member.getBalance())
                .willReturn(15000);

        // when
        int balance = creditGrantService.grantAdminCredit(
                memberId,
                amount,
                reason
        );

        // then
        assertThat(balance).isEqualTo(15000);

        ArgumentCaptor<CreditTransaction> captor =
                ArgumentCaptor.forClass(CreditTransaction.class);

        verify(creditTransactionRepository)
                .save(captor.capture());

        CreditTransaction saved = captor.getValue();

        assertThat(saved.getAmount()).isEqualTo(5000);
        assertThat(saved.getType())
                .isEqualTo(CreditTransactionType.ADMIN_GRANT);
        assertThat(saved.getBalanceAfter()).isEqualTo(15000);
        assertThat(saved.getReason()).isEqualTo(reason);
    }

    // 잘못된 지급액
    @ParameterizedTest
    @ValueSource(ints = {0, -1000})
    @DisplayName("0 이하의 금액은 크레딧 지급을 거절한다")
    void invalidAmount_throwsException(int amount) {
        // given
        Long memberId = 1L;

        // when
        BusinessException signupException = assertThrows(
                BusinessException.class,
                () -> creditGrantService.grantSignupCredit(
                        memberId,
                        amount
                )
        );

        BusinessException adminException = assertThrows(
                BusinessException.class,
                () -> creditGrantService.grantAdminCredit(
                        memberId,
                        amount,
                        "관리자 지급"
                )
        );

        // then
        assertThat(signupException.getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(adminException.getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);

        verifyNoInteractions(creditBalanceRepository);
    }

    // 지급 대상 회원 없음
    @Test
    @DisplayName("지급 대상 회원이 없으면 MEMBER_NOT_FOUND 예외가 발생한다")
    void memberNotFound() {
        // given
        Long memberId = 1L;
        int amount = 10000;

        given(creditBalanceRepository.increase(memberId, amount))
                .willReturn(0);

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> creditGrantService.grantSignupCredit(
                        memberId,
                        amount
                )
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);

        verify(creditTransactionRepository, never())
                .save(any(CreditTransaction.class));
    }

    // 관리자 지급 사유 누락
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("관리자 지급 사유가 없으면 지급을 거절한다")
    void invalidReason_throwsException(String reason) {
        // given
        Long memberId = 1L;
        int amount = 5000;

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> creditGrantService.grantAdminCredit(
                        memberId,
                        amount,
                        reason
                )
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);

        verifyNoInteractions(creditBalanceRepository);
    }

    // 관리자 지급 사유 501자 거절
    @Test
    @DisplayName("관리자 지급 사유가 500자를 초과하면 지급을 거절한다")
    void reasonOver500Characters_throwsException() {
        // given
        Long memberId = 1L;
        int amount = 5000;
        String reason = "a".repeat(501);

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> creditGrantService.grantAdminCredit(
                        memberId,
                        amount,
                        reason
                )
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);

        verifyNoInteractions(creditBalanceRepository);
    }

    // 관리자 지급 사유 500자 허용
    @Test
    @DisplayName("관리자 지급 사유가 500자이면 정상적으로 지급한다")
    void reason500Characters_success() {
        // given
        Long memberId = 1L;
        int amount = 5000;
        String reason = "a".repeat(500);

        Member member = mock(Member.class);

        given(creditBalanceRepository.increase(memberId, amount))
                .willReturn(1);
        given(memberRepository.findById(memberId))
                .willReturn(Optional.of(member));
        given(member.getBalance())
                .willReturn(5000);

        // when
        int balance = creditGrantService.grantAdminCredit(
                memberId,
                amount,
                reason
        );

        // then
        assertThat(balance).isEqualTo(5000);

        verify(creditTransactionRepository)
                .save(any(CreditTransaction.class));
    }
}