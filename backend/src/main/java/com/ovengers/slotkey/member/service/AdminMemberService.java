package com.ovengers.slotkey.member.service;

import com.ovengers.slotkey.audit.entity.AuditAction;
import com.ovengers.slotkey.audit.entity.AuditLog;
import com.ovengers.slotkey.audit.entity.AuditTargetType;
import com.ovengers.slotkey.audit.repository.AuditLogRepository;
import com.ovengers.slotkey.audit.service.AuditLogService;
import com.ovengers.slotkey.auth.service.VerificationGuardService;
import com.ovengers.slotkey.credit.repository.CreditTransactionRepository;
import com.ovengers.slotkey.credit.service.CreditGrantService;
import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.member.dto.response.*;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.entity.MemberRole;
import com.ovengers.slotkey.member.entity.MemberStatus;
import com.ovengers.slotkey.member.repository.MemberRepository;
import com.ovengers.slotkey.reservation.entity.Reservation;
import com.ovengers.slotkey.reservation.repository.ReservationRepository;
import com.ovengers.slotkey.space.entity.Space;
import com.ovengers.slotkey.space.repository.SpaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminMemberService {

    private final MemberRepository memberRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditLogService auditLogService;
    private final CreditGrantService creditGrantService;
    private final Clock clock;
    private final VerificationGuardService verificationGuardService;
    private final ReservationRepository reservationRepository;
    private final CreditTransactionRepository creditTransactionRepository;
    private final SpaceRepository spaceRepository;

    @Transactional(readOnly = true)
    public Page<AdminMemberResponse> getMembers(MemberStatus status, String keyword, Pageable pageable) {
        return memberRepository.searchMembers(keyword, status, pageable)
                .map(this::toAdminMemberResponse);
    }


    // 관리자 회원 상세 조회
    @Transactional(readOnly = true)
    public AdminMemberDetailResponse getMemberDetail(Long memberId) {

        Member member = findMember(memberId);

        AdminMemberResponse memberResponse =
                toAdminMemberResponse(member);

        long totalReservationCount =
                reservationRepository.countByMemberId(memberId);

        long suspensionCount =
                auditLogRepository.countByTargetTypeAndTargetIdAndActionIn(
                        AuditTargetType.MEMBER,
                        memberId,
                        List.of(
                                AuditAction.SUSPEND_MEMBER,
                                AuditAction.LOCK_MEMBER_BY_VERIFICATION
                        )
                );

        return new AdminMemberDetailResponse(
                memberResponse,
                totalReservationCount,
                suspensionCount
        );
    }

    @Transactional(readOnly = true)
    public Page<AdminMemberCreditTransactionResponse> getMemberCreditTransactions(
            Long memberId,
            Pageable pageable
    ) {
        findMember(memberId);

        Pageable sortedPageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );

        return creditTransactionRepository
                .findByMember_Id(memberId, sortedPageable)
                .map(AdminMemberCreditTransactionResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<AdminMemberReservationResponse> getMemberReservations(
            Long memberId,
            Pageable pageable
    ) {
        findMember(memberId);

        Pageable sortedPageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "id")
        );

        Page<Reservation> reservations =
                reservationRepository.findAllByMemberId(
                        memberId,
                        sortedPageable
                );

        List<Long> spaceIds = reservations.getContent().stream()
                .map(Reservation::getSpaceId)
                .distinct()
                .toList();

        Map<Long, String> spaceNames = spaceRepository
                .findAllById(spaceIds)
                .stream()
                .collect(Collectors.toMap(
                        Space::getId,
                        Space::getName
                ));

        return reservations.map(reservation ->
                AdminMemberReservationResponse.from(
                        reservation,
                        spaceNames.getOrDefault(
                                reservation.getSpaceId(),
                                "삭제된 공간"
                        )
                )
        );
    }

    @Transactional(readOnly = true)
    public Page<AdminMemberStatusHistoryResponse> getMemberStatusHistory(
            Long memberId,
            Pageable pageable
    ) {
        // 1. 회원 존재 여부 확인
        findMember(memberId);

        // 2. 최신 변경 기록부터 조회
        Pageable sortedPageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );

        // 3. 정지·복구·인증 잠금 기록 조회 후 DTO 변환
        return auditLogRepository
                .findByTargetTypeAndTargetIdAndActionIn(
                        AuditTargetType.MEMBER,
                        memberId,
                        List.of(
                                AuditAction.SUSPEND_MEMBER,
                                AuditAction.REACTIVATE_MEMBER,
                                AuditAction.LOCK_MEMBER_BY_VERIFICATION
                        ),
                        sortedPageable
                )
                .map(AdminMemberStatusHistoryResponse::from);
    }

    // 계정 정지
    @Transactional
    public MemberStatusChangeResponse suspend(Long memberId, String reason, Long adminMemberId) {
        return changeStatus(memberId, MemberStatus.SUSPENDED, reason, adminMemberId, AuditAction.SUSPEND_MEMBER);
    }

    // 계정 복구
    @Transactional
    public MemberStatusChangeResponse restore(Long memberId, String reason, Long adminMemberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if (member.getStatus() == MemberStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.MEMBER_WITHDRAWN);
        }
        if (member.getRole() == MemberRole.ADMIN) {
            throw new BusinessException(ErrorCode.TARGET_IS_ADMIN);
        }

        // 인증 처리와 같은 순서로 VerificationGuard 잠금을 먼저 확보한다.
        verificationGuardService.unlockAllForEmail(member.getEmail());
        return changeStatus(memberId, MemberStatus.ACTIVE, reason, adminMemberId, AuditAction.REACTIVATE_MEMBER);
    }

    // 관리자 크레딧 지급 (credit_transaction, audit_log 양쪽에 기록)
    @Transactional
    public AdminMemberResponse grantCredit(
            Long memberId,
            int amount,
            String reason,
            Long adminMemberId
    ) {
        if (adminMemberId.equals(memberId)) {
            throw new BusinessException(ErrorCode.SELF_GRANT_NOT_ALLOWED);
        }

        Member member = findMemberForUpdate(memberId);

        if (member.getStatus() == MemberStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.MEMBER_WITHDRAWN);
        }

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
                updatedMember.getBalance()
        );

        return toAdminMemberResponse(updatedMember);
    }


    private MemberStatusChangeResponse changeStatus(
            Long memberId,
            MemberStatus targetStatus,
            String reason,
            Long adminMemberId,
            AuditAction action) {
        Member member = findMemberForUpdate(memberId);

        if (member.getStatus() == MemberStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.MEMBER_WITHDRAWN);
        }

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
                .findFirstByTargetTypeAndTargetIdAndActionInOrderByCreatedAtDescIdDesc(
                        AuditTargetType.MEMBER,
                        member.getId(),
                        List.of(
                                AuditAction.SUSPEND_MEMBER,
                                AuditAction.REACTIVATE_MEMBER,
                                AuditAction.LOCK_MEMBER_BY_VERIFICATION
                        ))
                .orElse(null);

        LocalDateTime lastStatusChangedAt = lastStatusChangeLog != null ? lastStatusChangeLog.getCreatedAt() : null;
        String lastStatusChangeReason = lastStatusChangeLog != null ? lastStatusChangeLog.getReason() : null;

        return AdminMemberResponse.of(member, lastStatusChangedAt, lastStatusChangeReason);
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }


    private Member findMemberForUpdate(Long memberId) {
        return memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.MEMBER_NOT_FOUND)
                );
    }
}
