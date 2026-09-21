package com.ovengers.slotkey.member.service;

import com.ovengers.slotkey.audit.entity.AuditAction;
import com.ovengers.slotkey.audit.entity.AuditLog;
import com.ovengers.slotkey.audit.entity.AuditTargetType;
import com.ovengers.slotkey.audit.repository.AuditLogRepository;
import com.ovengers.slotkey.credit.entity.CreditTransaction;
import com.ovengers.slotkey.credit.entity.CreditTransactionType;
import com.ovengers.slotkey.credit.repository.CreditTransactionRepository;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.entity.MemberRole;
import com.ovengers.slotkey.member.entity.MemberStatus;
import com.ovengers.slotkey.member.repository.MemberRepository;
import com.ovengers.slotkey.support.FixedClockConfig;
import com.ovengers.slotkey.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@Import(FixedClockConfig.class)
class AdminMemberServiceIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private AdminMemberService adminMemberService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private CreditTransactionRepository creditTransactionRepository;

    @Autowired
    private com.ovengers.slotkey.access.repository.DoorAccessTokenRepository doorAccessTokenRepository;

    @Autowired
    private com.ovengers.slotkey.reservation.repository.ReservationStatusHistoryRepository reservationStatusHistoryRepository;

    @Autowired
    private com.ovengers.slotkey.reservation.repository.ReservationSlotRepository reservationSlotRepository;

    @Autowired
    private com.ovengers.slotkey.reservation.repository.ReservationRepository reservationRepository;

    @Autowired
    private com.ovengers.slotkey.space.repository.SpaceRepository spaceRepository;

    @SpyBean
    private AuditLogRepository auditLogRepository;

    private Member userMember;
    private Member adminMember;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAllInBatch();
        creditTransactionRepository.deleteAllInBatch();
        doorAccessTokenRepository.deleteAllInBatch();
        reservationStatusHistoryRepository.deleteAllInBatch();
        reservationSlotRepository.deleteAllInBatch();
        reservationRepository.deleteAllInBatch();
        spaceRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();

        userMember = memberRepository.save(new Member("user@slotkey.test", "hash", "일반회원"));
        adminMember = new Member("admin@slotkey.test", "hash", "관리자");
        ReflectionTestUtils.setField(adminMember, "role", MemberRole.ADMIN);
        adminMember = memberRepository.save(adminMember);
    }

    @Test
    @DisplayName("회원 정지 도중 감사 로그 저장이 실패하면 회원 상태 변경이 롤백되어 ACTIVE를 유지한다")
    void suspend_auditLogFails_rollsBackMemberStatus() {
        // given
        doThrow(new RuntimeException("Audit DB failure"))
                .when(auditLogRepository).save(any());

        // when & then
        assertThatThrownBy(() -> adminMemberService.suspend(userMember.getId(), "불량 이용", adminMember.getId()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Audit DB failure");

        // DB 재조회로 롤백 검증 (@Transactional 없이 독립 트랜잭션으로 조회)
        Member reloaded = memberRepository.findById(userMember.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(auditLogRepository.count()).isZero();
    }

    @Test
    @DisplayName("회원 복구 도중 감사 로그 저장이 실패하면 회원 상태 변경이 롤백되어 SUSPENDED를 유지한다")
    void restore_auditLogFails_rollsBackMemberStatus() {
        // given
        userMember.updateStatus(MemberStatus.SUSPENDED);
        memberRepository.save(userMember);

        doThrow(new RuntimeException("Audit DB failure"))
                .when(auditLogRepository).save(any());

        // when & then
        assertThatThrownBy(() -> adminMemberService.restore(userMember.getId(), "정지 해제", adminMember.getId()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Audit DB failure");

        // DB 재조회로 롤백 검증
        Member reloaded = memberRepository.findById(userMember.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(MemberStatus.SUSPENDED);
        assertThat(auditLogRepository.count()).isZero();
    }

    @Test
    @DisplayName("관리자 크레딧 지급 도중 감사 로그 저장이 실패하면 회원 잔액과 credit_transaction이 모두 롤백된다")
    void grantCredit_auditLogFails_rollsBackBalanceAndTransaction() {
        // given
        int initialBalance = userMember.getBalance();
        int grantAmount = 50000;

        doThrow(new RuntimeException("Audit DB failure"))
                .when(auditLogRepository).save(any());

        // when & then
        assertThatThrownBy(() -> adminMemberService.grantCredit(userMember.getId(), grantAmount, "보상 지급", adminMember.getId()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Audit DB failure");

        // DB 재조회로 롤백 검증
        Member reloaded = memberRepository.findById(userMember.getId()).orElseThrow();
        assertThat(reloaded.getBalance()).isEqualTo(initialBalance);
        assertThat(creditTransactionRepository.count()).isZero();
        assertThat(auditLogRepository.count()).isZero();
    }

    @Test
    @DisplayName("회원 정지가 정상 커밋되면 회원 상태가 SUSPENDED로 변경되고 감사 로그가 함께 저장된다")
    void suspend_success_commitsMemberStatusAndAudit() {
        // when
        adminMemberService.suspend(userMember.getId(), "불량 이용", adminMember.getId());

        // then: DB 재조회 검증
        Member reloaded = memberRepository.findById(userMember.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(MemberStatus.SUSPENDED);

        List<AuditLog> auditLogs = auditLogRepository.findAll();
        assertThat(auditLogs).hasSize(1);
        AuditLog auditLog = auditLogs.get(0);
        assertThat(auditLog.getActorMemberId()).isEqualTo(adminMember.getId());
        assertThat(auditLog.getAction()).isEqualTo(AuditAction.SUSPEND_MEMBER);
        assertThat(auditLog.getTargetType()).isEqualTo(AuditTargetType.MEMBER);
        assertThat(auditLog.getTargetId()).isEqualTo(userMember.getId());
        assertThat(auditLog.getReason()).isEqualTo("불량 이용");
        assertThat(auditLog.getBeforeValue()).contains("ACTIVE");
        assertThat(auditLog.getAfterValue()).contains("SUSPENDED");
        assertThat(auditLog.getCreatedAt()).isEqualTo(FixedClockConfig.FIXED_DATE_TIME);
    }

    @Test
    @DisplayName("회원 복구가 정상 커밋되면 회원 상태가 ACTIVE로 복구되고 감사 로그가 함께 저장된다")
    void restore_success_commitsMemberStatusAndAudit() {
        // given
        userMember.updateStatus(MemberStatus.SUSPENDED);
        memberRepository.save(userMember);

        // when
        adminMemberService.restore(userMember.getId(), "정지 해제", adminMember.getId());

        // then: DB 재조회 검증
        Member reloaded = memberRepository.findById(userMember.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(MemberStatus.ACTIVE);

        List<AuditLog> auditLogs = auditLogRepository.findAll();
        assertThat(auditLogs).hasSize(1);
        AuditLog auditLog = auditLogs.get(0);
        assertThat(auditLog.getActorMemberId()).isEqualTo(adminMember.getId());
        assertThat(auditLog.getAction()).isEqualTo(AuditAction.REACTIVATE_MEMBER);
        assertThat(auditLog.getTargetType()).isEqualTo(AuditTargetType.MEMBER);
        assertThat(auditLog.getTargetId()).isEqualTo(userMember.getId());
        assertThat(auditLog.getReason()).isEqualTo("정지 해제");
        assertThat(auditLog.getBeforeValue()).contains("SUSPENDED");
        assertThat(auditLog.getAfterValue()).contains("ACTIVE");
        assertThat(auditLog.getCreatedAt()).isEqualTo(FixedClockConfig.FIXED_DATE_TIME);
    }

    @Test
    @DisplayName("관리자 크레딧 지급이 정상 커밋되면 잔액과 credit_transaction, 감사 로그가 모두 함께 저장된다")
    void grantCredit_success_commitsBalanceTransactionAndAudit() {
        // when
        adminMemberService.grantCredit(userMember.getId(), 50000, "보상 지급", adminMember.getId());

        // then: DB 재조회 검증
        Member reloaded = memberRepository.findById(userMember.getId()).orElseThrow();
        assertThat(reloaded.getBalance()).isEqualTo(50000);

        List<CreditTransaction> txs = creditTransactionRepository.findAll();
        assertThat(txs).hasSize(1);
        CreditTransaction tx = txs.get(0);
        assertThat(tx.getAmount()).isEqualTo(50000);
        assertThat(tx.getBalanceAfter()).isEqualTo(50000);
        assertThat(tx.getType()).isEqualTo(CreditTransactionType.ADMIN_GRANT);
        assertThat(tx.getMember().getId()).isEqualTo(userMember.getId());

        List<AuditLog> auditLogs = auditLogRepository.findAll();
        assertThat(auditLogs).hasSize(1);
        AuditLog auditLog = auditLogs.get(0);
        assertThat(auditLog.getActorMemberId()).isEqualTo(adminMember.getId());
        assertThat(auditLog.getAction()).isEqualTo(AuditAction.GRANT_CREDIT);
        assertThat(auditLog.getTargetType()).isEqualTo(AuditTargetType.MEMBER);
        assertThat(auditLog.getTargetId()).isEqualTo(userMember.getId());
        assertThat(auditLog.getReason()).isEqualTo("보상 지급");
        assertThat(auditLog.getCreatedAt()).isEqualTo(FixedClockConfig.FIXED_DATE_TIME);
    }
}
