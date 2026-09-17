package com.ovengers.slotkey.reservation.service;

import com.ovengers.slotkey.access.entity.AccessDenyReason;
import com.ovengers.slotkey.access.entity.AccessResult;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminReservationServiceTest {

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

    private AdminReservationService adminReservationService;
    private LocalDateTime startTime;

    @BeforeEach
    void setUp() {
        adminReservationService = new AdminReservationService(
                reservationRepository, reservationSlotRepository, statusHistoryRepository,
                memberRepository, spaceRepository, auditLogService,
                doorAccessTokenService, doorAccessLogService);
        startTime = LocalDateTime.of(2026, 9, 18, 14, 0);
    }

    private Reservation confirmedReservation() {
        return Reservation.builder()
                .id(RESERVATION_ID)
                .memberId(MEMBER_ID)
                .spaceId(SPACE_ID)
                .startTime(startTime)
                .endTime(startTime.plusHours(1))
                .status(ReservationStatus.CONFIRMED)
                .pricePerSlotSnapshot(5000)
                .totalAmount(10000)
                .createdAt(startTime.minusDays(1))
                .build();
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
        given(memberRepository.findById(MEMBER_ID))
                .willReturn(Optional.of(new Member("user@slotkey.test", "{noop}password", "회원1")));
        given(spaceRepository.findById(SPACE_ID)).willReturn(Optional.of(
                Space.builder().name("공간1").build()));

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
    @DisplayName("이미 종료 상태(COMPLETED 등)인 예약은 강제 취소할 수 없고 후속 처리도 일어나지 않는다")
    void forceCancel_terminalState_throwsExceptionWithoutSideEffects() {
        Reservation completed = Reservation.builder()
                .id(RESERVATION_ID)
                .memberId(MEMBER_ID)
                .spaceId(SPACE_ID)
                .startTime(startTime)
                .endTime(startTime.plusHours(1))
                .status(ReservationStatus.COMPLETED)
                .pricePerSlotSnapshot(5000)
                .totalAmount(10000)
                .createdAt(startTime.minusDays(1))
                .build();
        given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.of(completed));

        assertThatThrownBy(() -> adminReservationService.forceCancel(RESERVATION_ID, REASON, ADMIN_MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESERVATION_STATE_CONFLICT);

        verify(reservationSlotRepository, never()).deleteByReservationId(any());
        verify(doorAccessTokenService, never()).revokeByReservation(any(), any(), any());
        verify(statusHistoryRepository, never()).save(any());
        verify(auditLogService, never()).log(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("강제 취소 성공 시 CANCELLED로 전이하고, 슬롯 반환·출입 토큰 revoke·이력 저장·감사 로그를 모두 수행한다")
    void forceCancel_success_cancelsSlotsTokenHistoryAndAudit() {
        Reservation reservation = confirmedReservation();
        given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.of(reservation));
        given(memberRepository.findById(MEMBER_ID))
                .willReturn(Optional.of(new Member("user@slotkey.test", "{noop}password", "회원1")));
        given(spaceRepository.findById(SPACE_ID)).willReturn(Optional.of(
                Space.builder().name("공간1").build()));

        adminReservationService.forceCancel(RESERVATION_ID, REASON, ADMIN_MEMBER_ID);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);

        verify(reservationSlotRepository).deleteByReservationId(RESERVATION_ID);
        verify(doorAccessTokenService).revokeByReservation(eq(RESERVATION_ID), any(), eq("ADMIN_FORCE_CANCEL"));

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
}
