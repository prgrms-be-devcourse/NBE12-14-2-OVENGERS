
package com.ovengers.slotkey.member.service;

import com.ovengers.slotkey.audit.entity.AuditAction;
import com.ovengers.slotkey.audit.entity.AuditTargetType;
import com.ovengers.slotkey.audit.repository.AuditLogRepository;
import com.ovengers.slotkey.audit.service.AuditLogService;
import com.ovengers.slotkey.auth.service.VerificationGuardService;
import com.ovengers.slotkey.credit.entity.CreditTransaction;
import com.ovengers.slotkey.credit.entity.CreditTransactionType;
import com.ovengers.slotkey.credit.repository.CreditTransactionRepository;
import com.ovengers.slotkey.credit.service.CreditGrantService;
import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.member.dto.response.AdminMemberCreditTransactionResponse;
import com.ovengers.slotkey.member.dto.response.AdminMemberDetailResponse;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.entity.MemberRole;
import com.ovengers.slotkey.member.entity.MemberStatus;
import com.ovengers.slotkey.member.repository.MemberRepository;
import com.ovengers.slotkey.reservation.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminMemberDetailServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private CreditGrantService creditGrantService;

    @Mock
    private VerificationGuardService verificationGuardService;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private CreditTransactionRepository creditTransactionRepository;

    private AdminMemberService service;

    @BeforeEach
    void setUp() {
        service = new AdminMemberService(
                memberRepository,
                auditLogRepository,
                auditLogService,
                creditGrantService,
                Clock.fixed(
                        Instant.parse("2026-09-18T04:00:00Z"),
                        ZoneId.of("Asia/Seoul")
                ),
                verificationGuardService,
                reservationRepository,
                creditTransactionRepository
        );
    }

    @Test
    @DisplayName("회원 상세 조회 시 회원 정보와 전체 예약 및 정지 기록 건수를 반환한다")
    void getMemberDetail_returnsMemberAndSummaryCounts() {
        // given
        Long memberId = 1L;
        Member member = mock(Member.class);

        given(memberRepository.findById(memberId))
                .willReturn(Optional.of(member));

        given(member.getId()).willReturn(memberId);
        given(member.getEmail()).willReturn("user@test.com");
        given(member.getNickname()).willReturn("테스트회원");
        given(member.getRole()).willReturn(MemberRole.USER);
        given(member.getStatus()).willReturn(MemberStatus.ACTIVE);
        given(member.getBalance()).willReturn(30000);

        given(auditLogRepository
                .findFirstByTargetTypeAndTargetIdAndActionInOrderByCreatedAtDescIdDesc(
                        AuditTargetType.MEMBER,
                        memberId,
                        List.of(
                                AuditAction.SUSPEND_MEMBER,
                                AuditAction.REACTIVATE_MEMBER,
                                AuditAction.LOCK_MEMBER_BY_VERIFICATION
                        )
                )).willReturn(Optional.empty());

        given(reservationRepository.countByMemberId(memberId))
                .willReturn(12L);

        given(auditLogRepository.countByTargetTypeAndTargetIdAndActionIn(
                AuditTargetType.MEMBER,
                memberId,
                List.of(
                        AuditAction.SUSPEND_MEMBER,
                        AuditAction.LOCK_MEMBER_BY_VERIFICATION
                )
        )).willReturn(1L);

        // when
        AdminMemberDetailResponse response =
                service.getMemberDetail(memberId);

        // then
        assertThat(response.member().memberId()).isEqualTo(memberId);
        assertThat(response.member().email()).isEqualTo("user@test.com");
        assertThat(response.member().nickname()).isEqualTo("테스트회원");
        assertThat(response.member().status()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(response.member().balance()).isEqualTo(30000);

        assertThat(response.totalReservationCount()).isEqualTo(12L);
        assertThat(response.suspensionCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("존재하지 않는 회원을 상세 조회하면 MEMBER_NOT_FOUND 예외가 발생한다")
    void getMemberDetail_memberNotFound() {
        // given
        Long memberId = 999L;

        given(memberRepository.findById(memberId))
                .willReturn(Optional.empty());

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.getMemberDetail(memberId)
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);

        verifyNoInteractions(reservationRepository, auditLogRepository);
    }

    @Test
    @DisplayName("회원 크레딧 거래 내역을 최신순으로 조회하고 페이지 정보와 함께 반환한다")
    void getMemberCreditTransactions_returnsPageSortedNewestFirst() {
        // given
        Long memberId = 1L;

        given(memberRepository.findById(memberId))
                .willReturn(Optional.of(mock(Member.class)));

        LocalDateTime createdAt =
                LocalDateTime.of(2026, 9, 18, 12, 0);

        CreditTransaction transaction = mock(CreditTransaction.class);

        given(transaction.getId()).willReturn(10L);
        given(transaction.getType())
                .willReturn(CreditTransactionType.TOP_UP);
        given(transaction.getAmount()).willReturn(50000);
        given(transaction.getBalanceAfter()).willReturn(80000);
        given(transaction.getCreatedAt()).willReturn(createdAt);

        Pageable requestedPage = PageRequest.of(1, 5);

        Pageable sortedPage = PageRequest.of(
                1,
                5,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );

        given(creditTransactionRepository.findByMember_Id(
                memberId,
                sortedPage
        )).willReturn(
                new PageImpl<>(
                        List.of(transaction),
                        sortedPage,
                        6L
                )
        );

        // when
        Page<AdminMemberCreditTransactionResponse> response =
                service.getMemberCreditTransactions(
                        memberId,
                        requestedPage
                );

        // then
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getTotalElements()).isEqualTo(6L);
        assertThat(response.getTotalPages()).isEqualTo(2);
        assertThat(response.getNumber()).isEqualTo(1);
        assertThat(response.getSize()).isEqualTo(5);

        AdminMemberCreditTransactionResponse transactionResponse =
                response.getContent().get(0);

        assertThat(transactionResponse.transactionId()).isEqualTo(10L);
        assertThat(transactionResponse.type())
                .isEqualTo(CreditTransactionType.TOP_UP);
        assertThat(transactionResponse.amount()).isEqualTo(50000);
        assertThat(transactionResponse.balanceAfter()).isEqualTo(80000);
        assertThat(transactionResponse.createdAt()).isEqualTo(createdAt);

        // Repository에 전달한 페이지 및 정렬 조건 확인
        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);

        verify(creditTransactionRepository)
                .findByMember_Id(eq(memberId), pageableCaptor.capture());

        Pageable usedPageable = pageableCaptor.getValue();

        assertThat(usedPageable.getPageNumber()).isEqualTo(1);
        assertThat(usedPageable.getPageSize()).isEqualTo(5);

        assertThat(usedPageable.getSort().getOrderFor("createdAt"))
                .isNotNull()
                .extracting(Sort.Order::getDirection)
                .isEqualTo(Sort.Direction.DESC);

        assertThat(usedPageable.getSort().getOrderFor("id"))
                .isNotNull()
                .extracting(Sort.Order::getDirection)
                .isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("존재하지 않는 회원의 크레딧 거래 내역을 조회하면 MEMBER_NOT_FOUND 예외가 발생한다")
    void getMemberCreditTransactions_memberNotFound() {
        // given
        Long memberId = 999L;

        given(memberRepository.findById(memberId))
                .willReturn(Optional.empty());

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.getMemberCreditTransactions(
                        memberId,
                        PageRequest.of(0, 20)
                )
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);

        verifyNoInteractions(creditTransactionRepository);
    }
}
