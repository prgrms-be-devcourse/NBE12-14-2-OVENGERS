package com.ovengers.slotkey.member.service;

import com.ovengers.slotkey.audit.entity.AuditAction;
import com.ovengers.slotkey.audit.entity.AuditLog;
import com.ovengers.slotkey.audit.entity.AuditTargetType;
import com.ovengers.slotkey.audit.repository.AuditLogRepository;
import com.ovengers.slotkey.audit.service.AuditLogService;
import com.ovengers.slotkey.credit.service.CreditGrantService;
import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.member.dto.response.AdminMemberResponse;
import com.ovengers.slotkey.member.dto.response.MemberStatusChangeResponse;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.entity.MemberRole;
import com.ovengers.slotkey.member.entity.MemberStatus;
import com.ovengers.slotkey.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminMemberServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private CreditGrantService creditGrantService;

    private Clock clock;
    private AdminMemberService adminMemberService;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(
                Instant.parse("2026-09-18T04:00:00Z"),
                ZoneId.of("Asia/Seoul")
        );

        adminMemberService = new AdminMemberService(
                memberRepository,
                auditLogRepository,
                auditLogService,
                creditGrantService,
                clock
        );
    }

    @Test
    @DisplayName("회원 검색 결과가 없으면 빈 페이지를 반환하고 상태 변경 이력을 조회하지 않는다")
    void getMembers_emptyResult_returnsEmptyPage() {
        // given
        Pageable pageable = PageRequest.of(0, 20);

        given(memberRepository.searchMembers(
                null,
                null,
                pageable
        )).willReturn(
                new PageImpl<>(
                        List.of(),
                        pageable,
                        0
                )
        );

        // when
        Page<AdminMemberResponse> result =
                adminMemberService.getMembers(
                        null,
                        null,
                        pageable
                );

        // then
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        verifyNoInteractions(auditLogRepository);
    }

    @Test
    @DisplayName("회원 목록 조회 시 최근 상태 변경 정보까지 응답한다")
    void getMembers_success() {
        // given
        Long memberId = 1L;
        Pageable pageable = PageRequest.of(0, 20);

        Member member = mock(Member.class);
        AuditLog auditLog = mock(AuditLog.class);

        LocalDateTime createdAt =
                LocalDateTime.of(2026, 9, 1, 10, 0);

        LocalDateTime lastStatusChangedAt =
                LocalDateTime.of(2026, 9, 17, 15, 0);

        given(member.getId()).willReturn(memberId);
        given(member.getEmail()).willReturn("user@test.com");
        given(member.getNickname()).willReturn("테스트회원");
        given(member.getRole()).willReturn(MemberRole.USER);
        given(member.getStatus()).willReturn(MemberStatus.SUSPENDED);
        given(member.getBalance()).willReturn(10000);
        given(member.getCreatedAt()).willReturn(createdAt);

        given(memberRepository.searchMembers(
                "user",
                MemberStatus.SUSPENDED,
                pageable
        )).willReturn(
                new PageImpl<>(
                        List.of(member),
                        pageable,
                        1
                )
        );

        given(
                auditLogRepository
                        .findFirstByTargetTypeAndTargetIdAndActionInOrderByCreatedAtDescIdDesc(
                                AuditTargetType.MEMBER,
                                memberId,
                                List.of(
                                        AuditAction.SUSPEND_MEMBER,
                                        AuditAction.REACTIVATE_MEMBER
                                )
                        )
        ).willReturn(Optional.of(auditLog));
        given(auditLog.getCreatedAt()).willReturn(lastStatusChangedAt);
        given(auditLog.getReason()).willReturn("운영 정책 위반");

        // when
        Page<AdminMemberResponse> result =
                adminMemberService.getMembers(
                        MemberStatus.SUSPENDED,
                        "user",
                        pageable
                );

        // then
        assertThat(result.getTotalElements()).isEqualTo(1);

        AdminMemberResponse response = result.getContent().get(0);

        assertThat(response.memberId()).isEqualTo(memberId);
        assertThat(response.email()).isEqualTo("user@test.com");
        assertThat(response.status()).isEqualTo(MemberStatus.SUSPENDED);
        assertThat(response.lastStatusChangedAt()).isEqualTo(lastStatusChangedAt);
        assertThat(response.lastStatusChangeReason()).isEqualTo("운영 정책 위반");
    }

    @Test
    @DisplayName("일반 회원을 정지하면 상태를 변경하고 감사 로그를 기록한다")
    void suspend_success() {
        // given
        Long memberId = 1L;
        Long adminMemberId = 100L;
        String reason = "운영 정책 위반";

        Member member = mock(Member.class);

        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));
        given(member.getId()).willReturn(memberId);
        given(member.getRole()).willReturn(MemberRole.USER);

        // 첫 번째 getStatus() -> 변경 전 상태
        // 두 번째 getStatus() -> 응답 생성 시 변경 후 상태
        given(member.getStatus())
                .willReturn(
                        MemberStatus.ACTIVE,
                        MemberStatus.SUSPENDED
                );

        // when
        MemberStatusChangeResponse response =
                adminMemberService.suspend(
                        memberId,
                        reason,
                        adminMemberId
                );

        // then
        verify(member).updateStatus(MemberStatus.SUSPENDED);

        verify(auditLogService)
                .log(
                        adminMemberId,
                        AuditAction.SUSPEND_MEMBER,
                        AuditTargetType.MEMBER,
                        memberId,
                        reason,
                        MemberStatus.ACTIVE,
                        MemberStatus.SUSPENDED
                );

        assertThat(response.memberId()).isEqualTo(memberId);
        assertThat(response.status()).isEqualTo(MemberStatus.SUSPENDED);
        assertThat(response.reason()).isEqualTo(reason);
        assertThat(response.changedAt())
                .isEqualTo(
                        LocalDateTime.of(
                                2026,
                                9,
                                18,
                                13,
                                0
                        )
                );
    }

    @Test
    @DisplayName("정지 회원을 복구하면 ACTIVE 상태로 변경하고 감사 로그를 기록한다")
    void restore_success() {
        // given
        Long memberId = 1L;
        Long adminMemberId = 100L;
        String reason = "정지 사유 해소";

        Member member = mock(Member.class);

        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));
        given(member.getId()).willReturn(memberId);
        given(member.getRole()).willReturn(MemberRole.USER);
        given(member.getStatus())
                .willReturn(
                        MemberStatus.SUSPENDED,
                        MemberStatus.ACTIVE
                );

        // when
        MemberStatusChangeResponse response =
                adminMemberService.restore(
                        memberId,
                        reason,
                        adminMemberId
                );

        // then
        verify(member).updateStatus(MemberStatus.ACTIVE);

        verify(auditLogService)
                .log(
                        adminMemberId,
                        AuditAction.REACTIVATE_MEMBER,
                        AuditTargetType.MEMBER,
                        memberId,
                        reason,
                        MemberStatus.SUSPENDED,
                        MemberStatus.ACTIVE
                );

        assertThat(response.status()).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("관리자 계정은 정지할 수 없다")
    void suspend_targetIsAdmin_throwsException() {
        // given
        Long memberId = 1L;

        Member member = mock(Member.class);

        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));
        given(member.getRole()).willReturn(MemberRole.ADMIN);

        // when
        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> adminMemberService.suspend(
                                memberId,
                                "정지 시도",
                                100L
                        )
                );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TARGET_IS_ADMIN);
        verify(member, never()).updateStatus(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    @DisplayName("관리자 계정은 복구 대상이 될 수 없다")
    void restore_targetIsAdmin_throwsException() {
        // given
        Long memberId = 1L;

        Member member = mock(Member.class);

        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));
        given(member.getRole()).willReturn(MemberRole.ADMIN);

        // when
        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> adminMemberService.restore(
                                memberId,
                                "복구 시도",
                                100L
                        )
                );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TARGET_IS_ADMIN);
        verify(member, never()).updateStatus(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    @DisplayName("존재하지 않는 회원을 정지하면 MEMBER_NOT_FOUND 예외가 발생한다")
    void suspend_memberNotFound_throwsException() {
        // given
        Long memberId = 999L;

        given(memberRepository.findById(memberId))
                .willReturn(Optional.empty());

        // when
        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> adminMemberService.suspend(
                                memberId,
                                "정지",
                                100L
                        )
                );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);

        verifyNoInteractions(auditLogService);
    }

    @Test
    @DisplayName("관리자가 회원에게 크레딧을 지급하면 지급 서비스와 감사 로그를 호출한다")
    void grantCredit_success() {
        // given
        Long memberId = 1L;
        Long adminMemberId = 100L;
        int amount = 50000;
        String reason = "보상 지급";

        Member beforeMember = mock(Member.class);
        Member updatedMember = mock(Member.class);

        given(memberRepository.findById(memberId))
                .willReturn(
                        Optional.of(beforeMember),
                        Optional.of(updatedMember)
                );

        given(beforeMember.getBalance()).willReturn(1000000);
        given(updatedMember.getId()).willReturn(memberId);
        given(updatedMember.getEmail()).willReturn("user@test.com");
        given(updatedMember.getNickname()).willReturn("일반회원");
        given(updatedMember.getRole()).willReturn(MemberRole.USER);
        given(updatedMember.getStatus()).willReturn(MemberStatus.ACTIVE);
        given(updatedMember.getBalance()).willReturn(1050000);

        given(updatedMember.getCreatedAt())
                .willReturn(
                        LocalDateTime.of(
                                2026,
                                9,
                                1,
                                10,
                                0
                        )
                );

        given(
                creditGrantService.grantAdminCredit(
                        memberId,
                        amount,
                        reason
                )
        ).willReturn(1050000);

        given(
                auditLogRepository
                        .findFirstByTargetTypeAndTargetIdAndActionInOrderByCreatedAtDescIdDesc(
                                AuditTargetType.MEMBER,
                                memberId,
                                List.of(
                                        AuditAction.SUSPEND_MEMBER,
                                        AuditAction.REACTIVATE_MEMBER
                                )
                        )
        ).willReturn(Optional.empty());

        // when
        AdminMemberResponse response =
                adminMemberService.grantCredit(
                        memberId,
                        amount,
                        reason,
                        adminMemberId
                );

        // then
        verify(creditGrantService)
                .grantAdminCredit(
                        memberId,
                        amount,
                        reason
                );

        verify(auditLogService)
                .log(
                        adminMemberId,
                        AuditAction.GRANT_CREDIT,
                        AuditTargetType.MEMBER,
                        memberId,
                        reason,
                        1000000,
                        1050000
                );

        assertThat(response.memberId()).isEqualTo(memberId);
        assertThat(response.balance()).isEqualTo(1050000);
        assertThat(response.lastStatusChangedAt()).isNull();
        assertThat(response.lastStatusChangeReason()).isNull();
    }

    @Test
    @DisplayName("관리자는 자기 자신에게 크레딧을 지급할 수 없다")
    void grantCredit_selfGrant_throwsException() {
        // given
        Long adminMemberId = 100L;

        // when
        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> adminMemberService.grantCredit(
                                adminMemberId,
                                50000,
                                "자기 자신 지급",
                                adminMemberId
                        )
                );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(
                        ErrorCode.SELF_GRANT_NOT_ALLOWED
                );

        verifyNoInteractions(
                memberRepository,
                creditGrantService,
                auditLogService
        );
    }

    @Test
    @DisplayName("존재하지 않는 회원에게 크레딧을 지급하면 MEMBER_NOT_FOUND 예외가 발생한다")
    void grantCredit_memberNotFound_throwsException() {
        // given
        Long memberId = 999L;

        given(memberRepository.findById(memberId)).willReturn(Optional.empty());

        // when
        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> adminMemberService.grantCredit(
                                memberId,
                                50000,
                                "지급",
                                100L
                        )
                );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND);

        verifyNoInteractions(
                creditGrantService,
                auditLogService
        );
    }
}