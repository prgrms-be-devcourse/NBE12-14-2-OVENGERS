package com.ovengers.slotkey.reservation.service;

import com.ovengers.slotkey.access.entity.DoorAccessLog;
import com.ovengers.slotkey.access.service.DoorAccessLogService;
import com.ovengers.slotkey.access.service.DoorAccessTokenService;
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
import com.ovengers.slotkey.reservation.repository.ReservationSlotRepository;
import com.ovengers.slotkey.reservation.repository.ReservationStatusHistoryRepository;
import com.ovengers.slotkey.space.entity.Space;
import com.ovengers.slotkey.space.repository.SpaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ovengers.slotkey.credit.service.CreditService;   // AuditLogService import 아래
import java.time.Clock;                                       // java.time.LocalDateTime import 위

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
    private final ReservationSlotRepository reservationSlotRepository;
    private final ReservationStatusHistoryRepository statusHistoryRepository;
    private final MemberRepository memberRepository;
    private final SpaceRepository spaceRepository;
    private final AuditLogService auditLogService;
    private final DoorAccessTokenService doorAccessTokenService;
    private final DoorAccessLogService doorAccessLogService;
    private final CreditService creditService;
    private final Clock clock;

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

        // 관리자 조회이므로 소유자 검사 없이(findResponsesByReservationId는 owner 검증 포함)
        // 예약 id로 출입 로그 엔티티를 직접 조회해 reservation 쪽 응답 DTO로 변환한다.
        List<DoorAccessLog> accessLogEntities = doorAccessLogService.findAllByReservationId(reservationId);
        List<DoorAccessLogResponse> accessLogs = accessLogEntities.stream()
                .map(DoorAccessLogResponse::from)
                .toList();

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
     * 이미 COMPLETED/EXPIRED/NO_SHOW/CANCELLED인 경우는 상태 변경 불가(409).
     *
     * 조건부 UPDATE(조회한 상태가 그대로일 때만 전이)가 문지기이며, 영향 행이 1일 때만 같은 트랜잭션에서
     * 후속 처리를 한다(core-domain-decisions 6-4). 그래서 본인 취소·체크인·배치와 겹쳐도 환불은 최대 1회다.
     * 결제한 예약(CONFIRMED/IN_USE)은 위약금 없이 total_amount 전액을 환불하고, 결제 전(HELD)은 환불하지 않는다.
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
        Long memberId = reservation.getMemberId();
        int totalAmount = reservation.getTotalAmount();
        LocalDateTime now = LocalDateTime.now(clock);

        // 문지기: 조회 시점의 상태가 그대로일 때만 CANCELLED로 전이한다. 0행이면 그 사이 다른 요청이 먼저 상태를 바꾼 것이다.
        int updated = reservationRepository.forceCancelIfStatusIs(
                reservationId, now, previousStatus, ReservationStatus.CANCELLED);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.RESERVATION_STATE_CONFLICT, "다른 요청이 먼저 예약 상태를 변경했습니다.");
        }

        // 상태 이력 저장
        ReservationStatusHistory history = ReservationStatusHistory.of(
                reservationId,
                adminMemberId,
                previousStatus,
                ReservationStatus.CANCELLED,
                reason,
                now
        );
        statusHistoryRepository.save(history);

        // 슬롯 삭제 (기존 취소와 동일)
        reservationSlotRepository.deleteByReservationId(reservationId);

        // 활성 출입 토큰 revoke. 관리자의 강제 취소이므로 소유자 검사가 없는
        // revokeByReservation(예약 상태 변경에 따른 시스템 경로)을 사용한다.
        doorAccessTokenService.revokeByReservation(reservationId, now, "ADMIN_FORCE_CANCEL");

        // 결제한 예약은 위약금 없이 전액 환불한다. HELD는 결제한 적이 없으므로 환불하지 않는다.
        if (previousStatus != ReservationStatus.HELD) {
            creditService.refund(memberId, reservationId, totalAmount);
        }

        // 감사 로그 기록
        auditLogService.log(
                adminMemberId,
                AuditAction.FORCE_CANCEL_RESERVATION,
                AuditTargetType.RESERVATION,
                reservationId,
                reason,
                previousStatus,
                ReservationStatus.CANCELLED
        );

        // 조건부 UPDATE가 영속성 컨텍스트를 비웠으므로 취소된 최신 상태를 다시 읽어 응답한다.
        Reservation cancelled = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));

        return AdminReservationResponse.from(
                cancelled,
                findMemberEmail(cancelled.getMemberId()),
                findSpaceName(cancelled.getSpaceId())
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
