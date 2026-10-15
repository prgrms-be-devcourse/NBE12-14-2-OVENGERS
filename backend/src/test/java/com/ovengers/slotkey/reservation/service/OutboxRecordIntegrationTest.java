package com.ovengers.slotkey.reservation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.outbox.OutboxEventRepository;
import com.ovengers.slotkey.reservation.dto.response.ReservationResponse;
import com.ovengers.slotkey.reservation.entity.ReservationStatus;
import com.ovengers.slotkey.credit.service.CreditServiceImpl;
import com.ovengers.slotkey.outbox.SlotReleasedRecorder;
import com.ovengers.slotkey.support.ReservationIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.*;

class OutboxRecordIntegrationTest extends ReservationIntegrationTestSupport {
    @Autowired private ReservationCancelService cancelService;
    @Autowired private AdminReservationService adminService;
    @Autowired private ReservationHoldExpirationService expirationService;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private SlotReleasedRecorder recorder;
    @Autowired private ReservationHoldService holdService;
    @SpyBean private CreditServiceImpl creditService;
    @SpyBean private OutboxEventRepository outboxEventRepository;

    @Test
    void recorderRequiresCallerTransaction() {
        assertThatThrownBy(() -> recorder.record(1L, 1L, SlotReleasedRecorder.Reason.CANCELLED))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void refundFailureRollsBackCancellationWithoutEvent() {
        // 환불은 outbox 기록보다 앞선 단계의 실패다. "이벤트가 없다"만 보여 줄 뿐 outbox 롤백의 증거는 아니다.
        Long member = createMember(0);
        Long space = createSpace();
        Long reservation = createReservation(member, space, ReservationStatus.CONFIRMED,
                tomorrowAt(14, 0), tomorrowAt(15, 0));
        doThrow(new IllegalStateException("refund failure")).when(creditService).refund(eq(member), eq(reservation), anyInt());
        assertThatThrownBy(() -> cancelService.cancel(member, reservation)).isInstanceOf(IllegalStateException.class);
        assertThat(events(reservation)).isEmpty();
        assertThat(statusOf(reservation)).isEqualTo("CONFIRMED");
        assertThat(countSlots(reservation)).isEqualTo(2);
    }

    @Test
    void cancellationRecordsOnePendingEvent() throws Exception {
        Long member = createMember(0);
        Long space = createSpace();
        Long reservation = createReservation(member, space, ReservationStatus.CONFIRMED,
                tomorrowAt(14, 0), tomorrowAt(15, 0));
        cancelService.cancel(member, reservation);
        assertEvent(reservation, space, "CANCELLED");
    }

    @ParameterizedTest
    @EnumSource(value = ReservationStatus.class, names = {"CONFIRMED", "HELD"})
    void forceCancellationRecordsOneEvent(ReservationStatus status) throws Exception {
        Long member = createMember(0);
        Long admin = createMember(0);
        Long space = createSpace();
        Long reservation = createReservation(member, space, status, tomorrowAt(14, 0), tomorrowAt(15, 0));
        adminService.forceCancel(reservation, "test", admin);
        assertEvent(reservation, space, "FORCE_CANCELLED");
    }

    @Test
    void forceCancellationOfInUseReservationRecordsOneEvent() throws Exception {
        Long member = createMember(0);
        Long admin = createMember(0);
        Long space = createSpace();
        Long reservation = createReservation(member, space, ReservationStatus.CONFIRMED,
                tomorrowAt(14, 0), tomorrowAt(15, 0));
        // chk_reservation_checked_in 제약 때문에 IN_USE 행은 checked_in_at과 함께 전이시켜야 한다.
        jdbcTemplate.update("UPDATE reservation SET status = 'IN_USE', checked_in_at = ? WHERE id = ?",
                now(), reservation);
        adminService.forceCancel(reservation, "test", admin);
        assertThat(statusOf(reservation)).isEqualTo("CANCELLED");
        assertEvent(reservation, space, "FORCE_CANCELLED");
    }

    @Test
    void expirationRecordsOnlyOnSuccessfulTransition() throws Exception {
        Long member = createMember(0);
        Long space = createSpace();
        Long reservation = createReservation(member, space, ReservationStatus.HELD,
                tomorrowAt(14, 0), tomorrowAt(15, 0));
        jdbcTemplate.update("UPDATE reservation SET hold_expires_at = ? WHERE id = ?", now().minusMinutes(1), reservation);
        transactionTemplate.executeWithoutResult(tx -> assertThat(expirationService.expireHold(reservation, now())).isTrue());
        assertEvent(reservation, space, "HOLD_EXPIRED");
        transactionTemplate.executeWithoutResult(tx -> assertThat(expirationService.expireHold(reservation, now())).isFalse());
        assertThat(events(reservation)).hasSize(1);
    }

    @Test
    void alreadyExpiredReservationRecordsNothing() {
        Long member = createMember(0);
        Long space = createSpace();
        Long reservation = createReservation(member, space, ReservationStatus.EXPIRED,
                tomorrowAt(14, 0), tomorrowAt(15, 0));
        transactionTemplate.executeWithoutResult(tx -> assertThat(expirationService.expireHold(reservation, now())).isFalse());
        assertThat(events(reservation)).isEmpty();
    }

    @Test
    void callerRollbackRemovesEventAndRestoresSlots() {
        Long member = createMember(0);
        Long space = createSpace();
        Long reservation = createReservation(member, space, ReservationStatus.CONFIRMED,
                tomorrowAt(14, 0), tomorrowAt(15, 0));
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(tx -> {
            cancelService.cancel(member, reservation);
            assertThat(events(reservation)).hasSize(1);
            throw new IllegalStateException("rollback after outbox insert");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(events(reservation)).isEmpty();
        assertThat(statusOf(reservation)).isEqualTo("CONFIRMED");
        assertThat(countSlots(reservation)).isEqualTo(2);
        assertThat(countLedger(reservation, "REFUND")).isZero();
    }

    @Test
    void recorderFailureRollsBackCancellation() {
        Long member = createMember(0);
        Long space = createSpace();
        Long reservation = createReservation(member, space, ReservationStatus.CONFIRMED,
                tomorrowAt(14, 0), tomorrowAt(15, 0));
        int balanceBefore = balanceOf(member);
        doThrow(new IllegalStateException("outbox insert failure")).when(outboxEventRepository).save(any());

        assertThatThrownBy(() -> cancelService.cancel(member, reservation))
                .isInstanceOf(IllegalStateException.class).hasMessage("outbox insert failure");

        assertThat(events(reservation)).isEmpty();
        assertThat(statusOf(reservation)).isEqualTo("CONFIRMED");
        assertThat(countSlots(reservation)).isEqualTo(2);
        assertThat(countLedger(reservation, "REFUND")).isZero();
        assertThat(countHistory(reservation, "CANCELLED")).isZero();
        assertThat(balanceOf(member)).isEqualTo(balanceBefore);
    }

    @Test
    void recorderFailureRollsBackForceCancellation() {
        Long member = createMember(0);
        Long admin = createMember(0);
        Long space = createSpace();
        Long reservation = createReservation(member, space, ReservationStatus.CONFIRMED,
                tomorrowAt(14, 0), tomorrowAt(15, 0));
        doThrow(new IllegalStateException("outbox insert failure")).when(outboxEventRepository).save(any());

        assertThatThrownBy(() -> adminService.forceCancel(reservation, "test", admin))
                .isInstanceOf(IllegalStateException.class).hasMessage("outbox insert failure");

        assertThat(events(reservation)).isEmpty();
        assertThat(statusOf(reservation)).isEqualTo("CONFIRMED");
        assertThat(countSlots(reservation)).isEqualTo(2);
        assertThat(countLedger(reservation, "REFUND")).isZero();
        assertThat(countHistory(reservation, "CANCELLED")).isZero();
    }

    @Test
    void holdCreationExpiringOtherMembersHoldRecordsOneEventAndCommitsNewSlots() throws Exception {
        Long owner = createMember(0);
        Long other = createMember(0);
        Long space = createSpace();
        Long expired = createReservation(owner, space, ReservationStatus.HELD, tomorrowAt(14, 0), tomorrowAt(15, 0));
        jdbcTemplate.update("UPDATE reservation SET hold_expires_at = ? WHERE id = ?", now().minusMinutes(1), expired);

        ReservationResponse created = holdService.createHold(other, space, tomorrowAt(14, 0), tomorrowAt(15, 0));

        assertThat(statusOf(expired)).isEqualTo("EXPIRED");
        assertThat(countSlots(expired)).isZero();
        assertEvent(expired, space, "HOLD_EXPIRED");
        assertThat(statusOf(created.reservationId())).isEqualTo("HELD");
        assertThat(countSlots(created.reservationId())).isEqualTo(2);
        assertThat(events(created.reservationId())).isEmpty();
    }

    @Test
    void slotConflictAfterExpiringHoldRollsBackEverythingWithoutEvent() {
        Long owner = createMember(0);
        Long other = createMember(0);
        Long space = createSpace();
        // 14:00~14:30은 살아있는 CONFIRMED, 14:30~15:00은 만료된 HELD. 14:00~15:00 HOLD는 만료 정리 뒤 슬롯 INSERT에서 충돌한다.
        createReservation(owner, space, ReservationStatus.CONFIRMED, tomorrowAt(14, 0), tomorrowAt(14, 30));
        Long expired = createReservation(owner, space, ReservationStatus.HELD, tomorrowAt(14, 30), tomorrowAt(15, 0));
        jdbcTemplate.update("UPDATE reservation SET hold_expires_at = ? WHERE id = ?", now().minusMinutes(1), expired);
        int reservationsBefore = countReservations(space);

        assertThatThrownBy(() -> holdService.createHold(other, space, tomorrowAt(14, 0), tomorrowAt(15, 0)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESERVATION_SLOT_CONFLICT);

        assertThat(events(expired)).isEmpty();
        assertThat(statusOf(expired)).isEqualTo("HELD");
        assertThat(countSlots(expired)).isEqualTo(1);
        assertThat(countReservations(space)).isEqualTo(reservationsBefore);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE partition_key = ?", Integer.class, space.toString())).isZero();
    }

    private int countReservations(Long space) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reservation WHERE space_id = ?", Integer.class, space);
    }

    private List<Map<String, Object>> events(Long reservation) {
        return jdbcTemplate.queryForList("SELECT * FROM outbox_event WHERE JSON_EXTRACT(payload, '$.reservationId') = ?", reservation);
    }

    private void assertEvent(Long reservation, Long space, String reason) throws Exception {
        List<Map<String, Object>> rows = events(reservation);
        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.getFirst();
        assertThat(row.get("event_type")).isEqualTo("SlotReleased");
        assertThat(row.get("topic")).isEqualTo("slot-released");
        assertThat(row.get("partition_key")).isEqualTo(space.toString());
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(((Number) row.get("attempts")).intValue()).isZero();
        assertThat(row.get("published_at")).isNull();
        JsonNode payload = objectMapper.readTree(row.get("payload").toString());
        assertThat(payload.size()).isEqualTo(4);
        assertThat(payload.get("eventId").asText()).isEqualTo(row.get("event_id"));
        assertThat(UUID.fromString(payload.get("eventId").asText())).isNotNull();
        assertThat(payload.get("reservationId").asLong()).isEqualTo(reservation);
        assertThat(payload.get("reason").asText()).isEqualTo(reason);
        LocalDateTime occurredAt = LocalDateTime.parse(payload.get("occurredAt").asText());
        assertThat(occurredAt).isBetween(LocalDateTime.now(clock).minusMinutes(1), LocalDateTime.now(clock));
        LocalDateTime createdAt = jdbcTemplate.queryForObject(
                "SELECT created_at FROM outbox_event WHERE event_id = ?", LocalDateTime.class, row.get("event_id"));
        assertThat(createdAt).isBetween(occurredAt.minusNanos(1000), occurredAt.plusNanos(1000));
    }
}
