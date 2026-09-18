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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminMemberService {

    private final MemberRepository memberRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditLogService auditLogService;
    private final CreditGrantService creditGrantService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Page<AdminMemberResponse> getMembers(MemberStatus status, String keyword, Pageable pageable) {
        return memberRepository.searchMembers(keyword, status, pageable)
                .map(this::toAdminMemberResponse);
    }

    // 계정 정지
    @Transactional
    public MemberStatusChangeResponse suspend(Long memberId, String reason, Long adminMemberId) {
        return changeStatus(memberId, MemberStatus.SUSPENDED, reason, adminMemberId, AuditAction.SUSPEND_MEMBER);
    }

    // 계정 복구
    @Transactional
    public MemberStatusChangeResponse restore(Long memberId, String reason, Long adminMemberId) {
        return changeStatus(memberId, MemberStatus.ACTIVE, reason, adminMemberId, AuditAction.REACTIVATE_MEMBER);
    }

    // 관리자 크레딧 지급 (credit_transaction, audit_log 양쪽에 기록)
    @Transactional
    public AdminMemberResponse grantCredit(Long memberId, int amount, String reason, Long adminMemberId) {
        if (adminMemberId.equals(memberId)) {
            throw new BusinessException(ErrorCode.SELF_GRANT_NOT_ALLOWED);
        }

        Member member = findMember(memberId);
        int beforeBalance = member.getBalance();

        creditGrantService.grantAdminCredit(memberId, amount, reason);

        Member updatedMember = findMember(memberId);

        auditLogService.log(
                adminMemberId,
                AuditAction.GRANT_CREDIT,
                AuditTargetType.MEMBER,
                memberId,
                reason,
                beforeBalance,
                updatedMember.getBalance());

        return toAdminMemberResponse(updatedMember);
    }

    private MemberStatusChangeResponse changeStatus(
            Long memberId,
            MemberStatus targetStatus,
            String reason,
            Long adminMemberId,
            AuditAction action) {
        Member member = findMember(memberId);

        // 관리자 계정은 정지/복구 대상이 될 수 없음
        if (member.getRole() == MemberRole.ADMIN) {
            throw new BusinessException(ErrorCode.TARGET_IS_ADMIN);
        }

        MemberStatus beforeStatus = member.getStatus();
        member.updateStatus(targetStatus);

        LocalDateTime changedAt = LocalDateTime.now(clock);

        // 감사 로그 기록
        auditLogService.log(
                adminMemberId,
                action,
                AuditTargetType.MEMBER,
                memberId,
                reason,
                beforeStatus,
                targetStatus);

        return MemberStatusChangeResponse.of(member, reason, changedAt);
    }

    // 회원의 최근 상태 변경(정지/복구) 감사 로그를 조회해 응답에 덧붙인다
    private AdminMemberResponse toAdminMemberResponse(Member member) {
        AuditLog lastStatusChangeLog = auditLogRepository
                .findFirstByTargetTypeAndTargetIdAndActionInOrderByCreatedAtDesc(
                        AuditTargetType.MEMBER,
                        member.getId(),
                        List.of(AuditAction.SUSPEND_MEMBER, AuditAction.REACTIVATE_MEMBER))
                .orElse(null);

        LocalDateTime lastStatusChangedAt = lastStatusChangeLog != null ? lastStatusChangeLog.getCreatedAt() : null;
        String lastStatusChangeReason = lastStatusChangeLog != null ? lastStatusChangeLog.getReason() : null;

        return AdminMemberResponse.of(member, lastStatusChangedAt, lastStatusChangeReason);
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }
}
