package com.ovengers.slotkey.reservation.service;

import com.ovengers.slotkey.access.entity.DoorAccessToken;
import com.ovengers.slotkey.access.repository.DoorAccessTokenRepository;
import com.ovengers.slotkey.audit.repository.AuditLogRepository;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.entity.MemberRole;
import com.ovengers.slotkey.member.repository.MemberRepository;
import com.ovengers.slotkey.reservation.entity.Reservation;
import com.ovengers.slotkey.reservation.entity.ReservationSlot;
import com.ovengers.slotkey.reservation.entity.ReservationStatus;
import com.ovengers.slotkey.reservation.entity.ReservationStatusHistory;
import com.ovengers.slotkey.reservation.repository.ReservationRepository;
import com.ovengers.slotkey.reservation.repository.ReservationSlotRepository;
import com.ovengers.slotkey.reservation.repository.ReservationStatusHistoryRepository;
import com.ovengers.slotkey.space.entity.Space;
import com.ovengers.slotkey.space.entity.SpaceStatus;
import com.ovengers.slotkey.space.repository.SpaceRepository;
import com.ovengers.slotkey.audit.entity.AuditAction;
import com.ovengers.slotkey.audit.entity.AuditLog;
import com.ovengers.slotkey.audit.entity.AuditTargetType;
import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.support.FixedClockConfig;
import com.ovengers.slotkey.support.IntegrationTestSupport;
import com.ovengers.slotkey.credit.entity.CreditTransaction;
import com.ovengers.slotkey.credit.entity.CreditTransactionType;
import com.ovengers.slotkey.credit.repository.CreditTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@Import(FixedClockConfig.class)
class AdminReservationServiceIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private AdminReservationService adminReservationService;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ReservationSlotRepository reservationSlotRepository;

    @Autowired
    private ReservationStatusHistoryRepository statusHistoryRepository;

    @Autowired
    private DoorAccessTokenRepository doorAccessTokenRepository;

    @Autowired
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    @Autowired
    private SpaceRepository spaceRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private CreditTransactionRepository creditTransactionRepository;

    @SpyBean
    private AuditLogRepository auditLogRepository;

    private Member userMember;
    private Member adminMember;
    private Space space;
    private Reservation reservation;
    private DoorAccessToken doorAccessToken;

    @BeforeEach
    void setUp() {
        creditTransactionRepository.deleteAllInBatch();
        auditLogRepository.deleteAllInBatch();
        doorAccessTokenRepository.deleteAllInBatch();
        statusHistoryRepository.deleteAllInBatch();
        reservationSlotRepository.deleteAllInBatch();
        reservationRepository.deleteAllInBatch();
        spaceRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();

        userMember = memberRepository.save(new Member("user@slotkey.test", "hash", "예약자"));
        adminMember = new Member("admin@slotkey.test", "hash", "관리자");
        ReflectionTestUtils.setField(adminMember, "role", MemberRole.ADMIN);
        adminMember = memberRepository.save(adminMember);

        space = spaceRepository.save(Space.builder()
                .name("회의실 A")
                .location("강남")
                .capacity(4)
                .pricePerSlot(3000L)
                .openingTime(LocalTime.of(9, 0))
                .closingTime(LocalTime.of(22, 0))
                .status(SpaceStatus.ACTIVE)
                .version(0)
                .build());

        LocalDateTime startTime = LocalDateTime.of(2026, 9, 20, 14, 0);
        LocalDateTime endTime = LocalDateTime.of(2026, 9, 20, 15, 0);

        reservation = reservationRepository.save(Reservation.builder()
                .memberId(userMember.getId())
                .spaceId(space.getId())
                .startTime(startTime)
                .endTime(endTime)
                .status(ReservationStatus.CONFIRMED)
                .pricePerSlotSnapshot(3000)
                .totalAmount(6000)
                .createdAt(startTime.minusDays(1))
                .build());

        reservationSlotRepository.save(ReservationSlot.of(reservation.getId(), space.getId(), startTime));
        reservationSlotRepository.save(ReservationSlot.of(reservation.getId(), space.getId(), startTime.plusMinutes(30)));

        doorAccessToken = doorAccessTokenRepository.save(new DoorAccessToken(
                reservation,
                "token-hash-123",
                startTime.minusHours(1)
        ));
    }

    @Test
    @DisplayName("관리자 예약 강제 취소 도중 감사 로그 저장이 실패하면 예약 상태, 슬롯, 도어 토큰 폐기, 상태 이력이 모두 롤백된다")
    void forceCancel_auditLogFails_rollsBackReservationSlotsTokenAndHistory() {
        // given
        doThrow(new RuntimeException("Audit DB failure"))
                .when(auditLogRepository).save(any());

        // when & then
        assertThatThrownBy(() -> adminReservationService.forceCancel(reservation.getId(), "관리자 직권 취소", adminMember.getId()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Audit DB failure");

        // 1. 예약 상태 롤백 검증: CONFIRMED 유지, cancelledAt은 null 유지
        Reservation reloadedReservation = reservationRepository.findById(reservation.getId()).orElseThrow();
        assertThat(reloadedReservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reloadedReservation.getCancelledAt()).isNull();

        // 2. 예약 슬롯 롤백 검증: 삭제되지 않고 2개 슬롯이 그대로 남아있음
        assertThat(reservationSlotRepository.count()).isEqualTo(2);

        // 3. 도어 토큰 롤백 검증: revoke 처리되지 않고 활성(revokedAt == null) 상태 유지
        DoorAccessToken reloadedToken = doorAccessTokenRepository.findById(doorAccessToken.getId()).orElseThrow();
        assertThat(reloadedToken.isRevoked()).isFalse();
        assertThat(reloadedToken.getRevokedAt()).isNull();

        // 4. 상태 이력 롤백 검증: 저장되지 않고 비어있음
        assertThat(statusHistoryRepository.findAllByReservationIdOrderByChangedAtAsc(reservation.getId())).isEmpty();

        // 5. 감사 로그 롤백 검증: 저장되지 않음
        assertThat(auditLogRepository.count()).isZero();

        // 6. 회원 잔액 및 크레딧 원장 롤백 검증: 잔액 초기값 유지, REFUND 원장 0건
        Member reloadedMember = memberRepository.findById(userMember.getId()).orElseThrow();
        assertThat(reloadedMember.getBalance()).isEqualTo(userMember.getBalance());
        assertThat(creditTransactionRepository.count()).isZero();
    }

    @Test
    @DisplayName("관리자 예약 강제 취소가 정상 커밋되면 예약 취소, 슬롯 삭제, 토큰 폐기, 상태 이력 및 감사 로그가 모두 반영된다")
    void forceCancel_success_commitsAllChangesAndAudit() {
        LocalDateTime expectedNow = FixedClockConfig.FIXED_DATE_TIME;

        // when
        adminReservationService.forceCancel(reservation.getId(), "정상 직권 취소", adminMember.getId());

        // 1. 예약 상태 확인
        Reservation reloadedReservation = reservationRepository.findById(reservation.getId()).orElseThrow();
        assertThat(reloadedReservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(reloadedReservation.getCancelledAt()).isEqualTo(expectedNow);

        // 2. 슬롯 삭제 확인
        assertThat(reservationSlotRepository.count()).isZero();

        // 3. 도어 토큰 폐기 확인
        DoorAccessToken reloadedToken = doorAccessTokenRepository.findById(doorAccessToken.getId()).orElseThrow();
        assertThat(reloadedToken.isRevoked()).isTrue();
        assertThat(reloadedToken.getRevokedAt()).isEqualTo(expectedNow);
        assertThat(reloadedToken.getRevokeReason()).isEqualTo("ADMIN_FORCE_CANCEL");

        // 4. 상태 이력 확인
        List<ReservationStatusHistory> histories =
                statusHistoryRepository.findAllByReservationIdOrderByChangedAtAsc(reservation.getId());
        assertThat(histories).hasSize(1);
        ReservationStatusHistory history = histories.get(0);
        assertThat(history.getChangedByMemberId()).isEqualTo(adminMember.getId());
        assertThat(history.getFromStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(history.getToStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(history.getReason()).isEqualTo("정상 직권 취소");
        assertThat(history.getChangedAt()).isEqualTo(expectedNow);

        // 5. 감사 로그 확인
        List<AuditLog> auditLogs = auditLogRepository.findAll();
        assertThat(auditLogs).hasSize(1);
        AuditLog auditLog = auditLogs.get(0);
        assertThat(auditLog.getActorMemberId()).isEqualTo(adminMember.getId());
        assertThat(auditLog.getAction()).isEqualTo(AuditAction.FORCE_CANCEL_RESERVATION);
        assertThat(auditLog.getTargetType()).isEqualTo(AuditTargetType.RESERVATION);
        assertThat(auditLog.getTargetId()).isEqualTo(reservation.getId());
        assertThat(auditLog.getReason()).isEqualTo("정상 직권 취소");
        assertThat(auditLog.getBeforeValue()).contains("CONFIRMED");
        assertThat(auditLog.getAfterValue()).contains("CANCELLED");
        assertThat(auditLog.getCreatedAt()).isEqualTo(expectedNow);

        // 6. 크레딧 환불 확인: 회원 잔액이 totalAmount(6000)만큼 증가하고 REFUND 원장이 정확히 1건 기록됨
        Member reloadedUser = memberRepository.findById(userMember.getId()).orElseThrow();
        assertThat(reloadedUser.getBalance()).isEqualTo(userMember.getBalance() + reservation.getTotalAmount());
        List<CreditTransaction> transactions = creditTransactionRepository.findAll();
        assertThat(transactions).hasSize(1);
        CreditTransaction transaction = transactions.get(0);
        assertThat(transaction.getMember().getId()).isEqualTo(userMember.getId());
        assertThat(transaction.getReservation().getId()).isEqualTo(reservation.getId());
        assertThat(transaction.getType()).isEqualTo(CreditTransactionType.REFUND);
        assertThat(transaction.getAmount()).isEqualTo(reservation.getTotalAmount());
        assertThat(transaction.getBalanceAfter()).isEqualTo(userMember.getBalance() + reservation.getTotalAmount());
    }

    @Test
    @DisplayName("강제 취소 시 이미 상태가 전이되었으면 조건부 UPDATE가 0행을 반환하여 409 예외가 발생하고 후속 처리가 롤백된다")
    void forceCancel_whenConcurrentTransitionOccurs_failsWithConflictAndAborts() {
        // given: 다른 트랜잭션이 이미 IN_USE -> COMPLETED로 전이 완료했다고 가정
        transactionTemplate.executeWithoutResult(status -> {
            reservationRepository.checkInIfConfirmed(
                    reservation.getId(),
                    LocalDateTime.of(2026, 9, 20, 14, 0),
                    ReservationStatus.CONFIRMED,
                    ReservationStatus.IN_USE
            );
            reservationRepository.checkOutIfInUse(
                    reservation.getId(),
                    LocalDateTime.of(2026, 9, 20, 15, 0),
                    ReservationStatus.IN_USE,
                    ReservationStatus.COMPLETED
            );
        });

        // when & then: 사전 검사 또는 조건부 UPDATE 단계에서 충돌 감지 (409)
        assertThatThrownBy(() -> adminReservationService.forceCancel(reservation.getId(), "취소 시도", adminMember.getId()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESERVATION_STATE_CONFLICT);

        // 슬롯은 유지(또는 완료 상태 유지), 감사 로그는 생성되지 않음
        assertThat(auditLogRepository.count()).isZero();
    }
}
