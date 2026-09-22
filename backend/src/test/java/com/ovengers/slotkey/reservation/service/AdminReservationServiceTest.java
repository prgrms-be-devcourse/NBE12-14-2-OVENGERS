package com.ovengers.slotkey.reservation.service;

import com.ovengers.slotkey.access.entity.AccessDenyReason;
import com.ovengers.slotkey.access.entity.AccessResult;
import com.ovengers.slotkey.access.entity.DoorAccessLog;
import com.ovengers.slotkey.access.service.DoorAccessLogService;
import com.ovengers.slotkey.access.service.DoorAccessTokenService;
import com.ovengers.slotkey.audit.entity.AuditAction;
import com.ovengers.slotkey.audit.entity.AuditTargetType;
import com.ovengers.slotkey.audit.service.AuditLogService;
import com.ovengers.slotkey.credit.service.CreditService;
import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.repository.MemberRepository;
import com.ovengers.slotkey.reservation.dto.response.AdminReservationDetailResponse;
import com.ovengers.slotkey.reservation.dto.response.AdminReservationResponse;
import com.ovengers.slotkey.reservation.entity.Reservation;
import com.ovengers.slotkey.reservation.entity.ReservationStatus;
import com.ovengers.slotkey.reservation.entity.ReservationStatusHistory;
import com.ovengers.slotkey.reservation.repository.ReservationRepository;
import com.ovengers.slotkey.reservation.repository.ReservationSlotRepository;
import com.ovengers.slotkey.reservation.repository.ReservationStatusHistoryRepository;
import com.ovengers.slotkey.space.entity.Space;
import com.ovengers.slotkey.space.repository.SpaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminReservationServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final Long RESERVATION_ID = 10L;
    private static final Long MEMBER_ID = 1L;
    private static final Long SPACE_ID = 5L;
    private static final Long ADMIN_MEMBER_ID = 99L;
    private static final String REASON = "고객 요청에 의한 강제 취소";

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private ReservationSlotRepository reservationSlotRepository;
    @Mock
    private ReservationStatusHistoryRepository statusHistoryRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private SpaceRepository spaceRepository;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private DoorAccessTokenService doorAccessTokenService;
    @Mock
    private DoorAccessLogService doorAccessLogService;
    @Mock
    private CreditService creditService;

    private AdminReservationService adminReservationService;
    private final LocalDateTime now = LocalDateTime.of(2026, 9, 17, 12, 0);
    private LocalDateTime startTime;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(now.atZone(ZONE).toInstant(), ZONE);
        adminReservationService = new AdminReservationService(
                reservationRepository, reservationSlotRepository, statusHistoryRepository,
                memberRepository, spaceRepository, auditLogService,
                doorAccessTokenService, doorAccessLogService, creditService, clock);
        startTime = LocalDateTime.of(2026, 9, 18, 14, 0);
    }

    private Reservation confirmedReservation() {
        return reservationWithStatus(ReservationStatus.CONFIRMED, 10000);
    }

    private Reservation reservationWithStatus(ReservationStatus status, int totalAmount) {
        return Reservation.builder()
                .id(RESERVATION_ID)
                .memberId(MEMBER_ID)
                .spaceId(SPACE_ID)
                .startTime(startTime)
                .endTime(startTime.plusHours(1))
                .status(status)
                .pricePerSlotSnapshot(5000)
                .totalAmount(totalAmount)
                .createdAt(startTime.minusDays(1))
                .build();
    }

    /** 응답 조립에 쓰이는 회원 이메일·공간 이름 조회를 준비한다. */
    private void stubMemberAndSpace() {
        given(memberRepository.findById(MEMBER_ID))
                .willReturn(Optional.of(new Member("user@slotkey.test", "{noop}password", "회원1")));
        given(spaceRepository.findById(SPACE_ID)).willReturn(Optional.of(
                Space.builder().name("공간1").build()));
    }

    // ---------- getReservationDetail ----------

    @Test
    @DisplayName("존재하지 않는 예약을 상세 조회하면 RESERVATION_NOT_FOUND 예외가 발생한다")
    void getReservationDetail_reservationNotFound_throwsException() {
        given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminReservationService.getReservationDetail(RESERVATION_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESERVATION_NOT_FOUND);
    }

    @Test
    @DisplayName("예약 상세 조회 시 소유자 검사 없이 출입 로그를 조회해 상태 이력과 함께 응답에 담는다")
    void getReservationDetail_success_mapsStatusHistoryAndAccessLogs() {
        Reservation reservation = confirmedReservation();
        given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.of(reservation));
        stubMemberAndSpace();

        ReservationStatusHistory history = ReservationStatusHistory.of(
                RESERVATION_ID, MEMBER_ID, null, ReservationStatus.CONFIRMED, null, startTime.minusHours(1));
        given(statusHistoryRepository.findAllByReservationIdOrderByChangedAtAsc(RESERVATION_ID))
                .willReturn(List.of(history));

        DoorAccessLog allowLog = new DoorAccessLog(
                null, null, null, AccessResult.ALLOW, null, startTime.plusMinutes(1));
        DoorAccessLog denyLog = new DoorAccessLog(
                null, null, null, AccessResult.DENY, AccessDenyReason.TOKEN_REVOKED, startTime.plusMinutes(2));
        given(doorAccessLogService.findAllByReservationId(RESERVATION_ID))
                .willReturn(List.of(allowLog, denyLog));

        AdminReservationDetailResponse response = adminReservationService.getReservationDetail(RESERVATION_ID);

        // 관리자는 예약 소유자가 아니어도 조회할 수 있어야 하므로, owner 검사가 있는
        // findResponsesByReservationId가 아니라 findAllByReservationId가 호출되어야 한다.
        verify(doorAccessLogService).findAllByReservationId(RESERVATION_ID);
        verify(doorAccessLogService, never()).findResponsesByReservationId(any(), any());

        assertThat(response.statusHistory()).hasSize(1);
        assertThat(response.accessLogs()).hasSize(2);
        assertThat(response.accessLogs().get(0).result()).isEqualTo(AccessResult.ALLOW);
        assertThat(response.accessLogs().get(1).result()).isEqualTo(AccessResult.DENY);
        assertThat(response.accessLogs().get(1).reasonCode()).isEqualTo(AccessDenyReason.TOKEN_REVOKED);
    }

    // ---------- forceCancel ----------

    @Test
    @DisplayName("존재하지 않는 예약을 강제 취소하면 RESERVATION_NOT_FOUND 예외가 발생한다")
    void forceCancel_reservationNotFound_throwsException() {
        given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminReservationService.forceCancel(RESERVATION_ID, REASON, ADMIN_MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESERVATION_NOT_FOUND);
    }

    @Test
    @DisplayName("이미 종료 상태(COMPLETED 등)인 예약은 강제 취소할 수 없고 환불을 포함한 후속 처리도 일어나지 않는다")
    void forceCancel_terminalState_throwsExceptionWithoutSideEffects() {
        Reservation completed = reservationWithStatus(ReservationStatus.COMPLETED, 10000);
        given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.of(completed));

        assertThatThrownBy(() -> adminReservationService.forceCancel(RESERVATION_ID, REASON, ADMIN_MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESERVATION_STATE_CONFLICT);

        verify(reservationRepository, never()).forceCancelIfStatusIs(any(), any(), any(), any());
        verify(reservationSlotRepository, never()).deleteByReservationId(any());
        verify(doorAccessTokenService, never()).revokeByReservation(any(), any(), any());
        verify(creditService, never()).refund(any(), any(), anyInt());
        verify(statusHistoryRepository, never()).save(any());
        verify(auditLogService, never()).log(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("CONFIRMED 예약을 강제 취소하면 CANCELLED로 전이하고 슬롯 반환·토큰 revoke·이력·전액 환불·감사 로그를 모두 수행한다")
    void forceCancel_confirmed_cancelsAndRefundsFullAmount() {
        Reservation confirmed = confirmedReservation();
        Reservation cancelled = reservationWithStatus(ReservationStatus.CANCELLED, 10000);
        given(reservationRepository.findById(RESERVATION_ID))
                .willReturn(Optional.of(confirmed), Optional.of(cancelled));
        given(reservationRepository.forceCancelIfStatusIs(
                RESERVATION_ID, now, ReservationStatus.CONFIRMED, ReservationStatus.CANCELLED)).willReturn(1);
        stubMemberAndSpace();

        AdminReservationResponse response =
                adminReservationService.forceCancel(RESERVATION_ID, REASON, ADMIN_MEMBER_ID);

        assertThat(response.status()).isEqualTo(ReservationStatus.CANCELLED);

        verify(reservationSlotRepository).deleteByReservationId(RESERVATION_ID);
        verify(doorAccessTokenService).revokeByReservation(eq(RESERVATION_ID), eq(now), eq("ADMIN_FORCE_CANCEL"));

        // 관리자 사유의 취소이므로 위약금 없이 결제액 전액을 돌려준다.
        verify(creditService).refund(MEMBER_ID, RESERVATION_ID, 10000);
        verify(creditService, never()).penalize(any(), any(), anyInt());

        verify(statusHistoryRepository).save(argThat(history ->
                history.getReservationId().equals(RESERVATION_ID)
                        && history.getChangedByMemberId().equals(ADMIN_MEMBER_ID)
                        && history.getFromStatus() == ReservationStatus.CONFIRMED
                        && history.getToStatus() == ReservationStatus.CANCELLED
                        && history.getReason().equals(REASON)));

        verify(auditLogService).log(
                eq(ADMIN_MEMBER_ID),
                eq(AuditAction.FORCE_CANCEL_RESERVATION),
                eq(AuditTargetType.RESERVATION),
                eq(RESERVATION_ID),
                eq(REASON),
                eq(ReservationStatus.CONFIRMED),
                eq(ReservationStatus.CANCELLED));
    }

    @Test
    @DisplayName("연장으로 늘어난 총액을 가진 예약을 강제 취소하면 연장분까지 포함한 total_amount 전액을 환불한다")
    void forceCancel_extendedReservation_refundsTotalAmountIncludingExtension() {
        Reservation extended = reservationWithStatus(ReservationStatus.CONFIRMED, 25000);
        Reservation cancelled = reservationWithStatus(ReservationStatus.CANCELLED, 25000);
        given(reservationRepository.findById(RESERVATION_ID))
                .willReturn(Optional.of(extended), Optional.of(cancelled));
        given(reservationRepository.forceCancelIfStatusIs(
                RESERVATION_ID, now, ReservationStatus.CONFIRMED, ReservationStatus.CANCELLED)).willReturn(1);
        stubMemberAndSpace();

        adminReservationService.forceCancel(RESERVATION_ID, REASON, ADMIN_MEMBER_ID);

        verify(creditService).refund(MEMBER_ID, RESERVATION_ID, 25000);
    }

    @Test
    @DisplayName("이미 이용 중(IN_USE)인 예약을 강제 취소해도 전액 환불한다")
    void forceCancel_inUse_refundsFullAmount() {
        Reservation inUse = reservationWithStatus(ReservationStatus.IN_USE, 10000);
        Reservation cancelled = reservationWithStatus(ReservationStatus.CANCELLED, 10000);
        given(reservationRepository.findById(RESERVATION_ID))
                .willReturn(Optional.of(inUse), Optional.of(cancelled));
        given(reservationRepository.forceCancelIfStatusIs(
                RESERVATION_ID, now, ReservationStatus.IN_USE, ReservationStatus.CANCELLED)).willReturn(1);
        stubMemberAndSpace();

        adminReservationService.forceCancel(RESERVATION_ID, REASON, ADMIN_MEMBER_ID);

        verify(creditService).refund(MEMBER_ID, RESERVATION_ID, 10000);
        verify(statusHistoryRepository).save(argThat(history ->
                history.getFromStatus() == ReservationStatus.IN_USE
                        && history.getToStatus() == ReservationStatus.CANCELLED));
    }

    @Test
    @DisplayName("결제 전(HELD) 예약을 강제 취소하면 슬롯만 반환하고 환불은 하지 않는다")
    void forceCancel_held_doesNotRefund() {
        Reservation held = reservationWithStatus(ReservationStatus.HELD, 10000);
        Reservation cancelled = reservationWithStatus(ReservationStatus.CANCELLED, 10000);
        given(reservationRepository.findById(RESERVATION_ID))
                .willReturn(Optional.of(held), Optional.of(cancelled));
        given(reservationRepository.forceCancelIfStatusIs(
                RESERVATION_ID, now, ReservationStatus.HELD, ReservationStatus.CANCELLED)).willReturn(1);
        stubMemberAndSpace();

        adminReservationService.forceCancel(RESERVATION_ID, REASON, ADMIN_MEMBER_ID);

        verify(reservationSlotRepository).deleteByReservationId(RESERVATION_ID);
        verify(creditService, never()).refund(any(), any(), anyInt());
        verify(creditService, never()).penalize(any(), any(), anyInt());
    }

    @Test
    @DisplayName("조회 뒤 다른 요청이 먼저 상태를 바꿔 조건부 UPDATE가 0행이면 RESERVATION_STATE_CONFLICT가 발생하고 환불을 포함한 후속 처리가 일어나지 않는다")
    void forceCancel_statusChangedConcurrently_throwsConflictWithoutSideEffects() {
        given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.of(confirmedReservation()));
        given(reservationRepository.forceCancelIfStatusIs(
                RESERVATION_ID, now, ReservationStatus.CONFIRMED, ReservationStatus.CANCELLED)).willReturn(0);

        assertThatThrownBy(() -> adminReservationService.forceCancel(RESERVATION_ID, REASON, ADMIN_MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESERVATION_STATE_CONFLICT);

        verify(creditService, never()).refund(any(), any(), anyInt());
        verify(reservationSlotRepository, never()).deleteByReservationId(any());
        verify(doorAccessTokenService, never()).revokeByReservation(any(), any(), any());
        verify(statusHistoryRepository, never()).save(any());
        verify(auditLogService, never()).log(any(), any(), any(), any(), any(), any(), any());
    }
}