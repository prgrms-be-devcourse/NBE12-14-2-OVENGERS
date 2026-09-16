package com.ovengers.slotkey.reservation.service;

import com.ovengers.slotkey.audit.entity.AuditAction;
import com.ovengers.slotkey.audit.entity.AuditTargetType;
import com.ovengers.slotkey.audit.service.AuditLogService;
import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.repository.MemberRepository;
import com.ovengers.slotkey.reservation.dto.response.AdminReservationDetailResponse;
import com.ovengers.slotkey.reservation.dto.response.AdminReservationResponse;
import com.ovengers.slotkey.reservation.dto.response.DoorAccessLogResponse;
import com.ovengers.slotkey.reservation.dto.response.ReservationStatusHistoryResponse;
import com.ovengers.slotkey.reservation.entity.Reservation;
import com.ovengers.slotkey.reservation.entity.ReservationStatus;
import com.ovengers.slotkey.reservation.entity.ReservationStatusHistory;
import com.ovengers.slotkey.reservation.repository.ReservationRepository;
import com.ovengers.slotkey.reservation.repository.ReservationStatusHistoryRepository;
import com.ovengers.slotkey.space.entity.Space;
import com.ovengers.slotkey.space.repository.SpaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 예약 조회 및 강제 취소 서비스 (core-domain-decisions 3-4).
 *
 * 강제 취소는 "조건부 UPDATE 영향 행이 1일 때만 후속 처리"(core-domain-decisions 3-1) 패턴을 따르며,
 * 예약자 본인의 취소와 달리 관리자는 상태·시간에 상관없이 취소할 수 있다 (사유 기록).
 */
@Service
@RequiredArgsConstructor
public class AdminReservationService {
    private final ReservationRepository reservationRepository;
    private final ReservationStatusHistoryRepository statusHistoryRepository;
    private final MemberRepository memberRepository;
    private final SpaceRepository spaceRepository;
    private final AuditLogService auditLogService;

    /**
     * 전체 예약 조회 (페이지네이션).
     */
    @Transactional(readOnly = true)
    public Page<AdminReservationResponse> findAllReservations(Pageable pageable) {
        return reservationRepository.findAll(pageable)
                .map(reservation -> AdminReservationResponse.from(
                        reservation,
                        findMemberEmail(reservation.getMemberId()),
                        findSpaceName(reservation.getSpaceId())
                ));
    }

    /**
     * 예약 상세 조회 (상태 이력 + 출입 로그 포함).
     * 관리자는 다른 회원의 예약도 조회할 수 있다.
     */
    @Transactional(readOnly = true)
    public AdminReservationDetailResponse getReservationDetail(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));

        List<ReservationStatusHistory> statusHistories =
                statusHistoryRepository.findAllByReservationIdOrderByChangedAtAsc(reservationId);
        List<ReservationStatusHistoryResponse> historyResponses = statusHistories.stream()
                .map(ReservationStatusHistoryResponse::from)
                .toList();

        // TODO(박창현님): access 도메인에서 DoorAccessLog 조회로 대체
        List<DoorAccessLogResponse> accessLogs = List.of();

        return AdminReservationDetailResponse.from(
                reservation,
                findMemberEmail(reservation.getMemberId()),
                findSpaceName(reservation.getSpaceId()),
                historyResponses,
                accessLogs
        );
    }

    /**
     * 예약 강제 취소 (관리자만, 사유 기록).
     * 상태·시간에 상관없이 CANCELLED로 전이할 수 있다.
     * 이미 COMPLETED/EXPIRED/NO_SHOW인 경우는 상태 변경 불가(409).
     */
    @Transactional
    public AdminReservationResponse forceCancel(Long reservationId, String reason, Long adminMemberId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));

        // 이미 종료된 상태는 취소할 수 없음
        if (reservation.isTerminalState()) {
            throw new BusinessException(ErrorCode.RESERVATION_STATE_CONFLICT);
        }

        ReservationStatus previousStatus = reservation.getStatus();

        // 상태 전이
        reservation.cancelByAdmin();

        // 상태 이력 저장
        ReservationStatusHistory history = ReservationStatusHistory.of(
                reservation.getId(),
                adminMemberId,
                previousStatus,
                reservation.getStatus(),
                reason,
                LocalDateTime.now()
        );
        statusHistoryRepository.save(history);

        // 슬롯 삭제 (기존 취소와 동일)
        // TODO: ReservationSlotRepository.deleteByReservationId(reservationId);

        // TODO(박창현님): 활성 출입 토큰 revoke

        // 감사 로그 기록
        auditLogService.log(
                adminMemberId,
                AuditAction.FORCE_CANCEL_RESERVATION,
                AuditTargetType.RESERVATION,
                reservationId,
                reason,
                previousStatus,
                reservation.getStatus()
        );

        return AdminReservationResponse.from(
                reservation,
                findMemberEmail(reservation.getMemberId()),
                findSpaceName(reservation.getSpaceId())
        );
    }

    private String findMemberEmail(Long memberId) {
        return memberRepository.findById(memberId)
                .map(Member::getEmail)
                .orElse(null);
    }

    private String findSpaceName(Long spaceId) {
        return spaceRepository.findById(spaceId)
                .map(Space::getName)
                .orElse(null);
    }
}
