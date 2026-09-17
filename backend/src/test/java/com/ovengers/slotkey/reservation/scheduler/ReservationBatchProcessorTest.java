package com.ovengers.slotkey.reservation.scheduler;

import com.ovengers.slotkey.access.service.DoorAccessTokenService;
import com.ovengers.slotkey.reservation.entity.Reservation;
import com.ovengers.slotkey.reservation.entity.ReservationStatus;
import com.ovengers.slotkey.reservation.repository.ReservationRepository;
import com.ovengers.slotkey.reservation.repository.ReservationSlotRepository;
import com.ovengers.slotkey.reservation.repository.ReservationStatusHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class ReservationBatchProcessorTest {

    private static final Long RESERVATION_ID = 10L;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 17, 15, 0);
    private static final LocalDateTime END_TIME = LocalDateTime.of(2026, 9, 17, 14, 0);

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ReservationSlotRepository reservationSlotRepository;

    @Mock
    private ReservationStatusHistoryRepository reservationStatusHistoryRepository;

    @Mock
    private DoorAccessTokenService doorAccessTokenService;

    @Mock
    private Reservation completedReservation;

    private ReservationBatchProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new ReservationBatchProcessor(
                reservationRepository,
                reservationSlotRepository,
                reservationStatusHistoryRepository,
                doorAccessTokenService
        );
    }

    @Test
    @DisplayName("노쇼 처리에 성공하면 출입 토큰을 폐기하고 상태 이력을 저장한다")
    void shouldRevokeTokenAndSaveHistoryWhenMarkNoShowSucceeds() {
        given(reservationRepository.markNoShowIfNotCheckedIn(
                RESERVATION_ID,
                NOW.minus(ReservationBatchProcessor.CHECK_IN_WINDOW),
                ReservationStatus.CONFIRMED,
                ReservationStatus.NO_SHOW
        )).willReturn(1);

        boolean result = processor.markNoShow(
                RESERVATION_ID,
                NOW
        );

        assertThat(result).isTrue();

        verify(reservationSlotRepository).deleteByReservationId(RESERVATION_ID);

        verify(doorAccessTokenService).revokeByReservation(
                RESERVATION_ID,
                NOW,
                "NO_SHOW"
        );

        verify(reservationStatusHistoryRepository).save(argThat(history ->
                history.getReservationId().equals(RESERVATION_ID)
                        && history.getChangedByMemberId() == null
                        && history.getFromStatus() == ReservationStatus.CONFIRMED
                        && history.getToStatus() == ReservationStatus.NO_SHOW
                        && history.getReason().equals("NO_SHOW")
                        && history.getChangedAt().equals(NOW)
        ));
    }

    @Test
    @DisplayName("노쇼 상태 전이에 실패하면 출입 토큰을 폐기하지 않는다")
    void shouldNotRevokeTokenWhenMarkNoShowTransitionFails() {
        given(reservationRepository.markNoShowIfNotCheckedIn(
                RESERVATION_ID,
                NOW.minus(ReservationBatchProcessor.CHECK_IN_WINDOW),
                ReservationStatus.CONFIRMED,
                ReservationStatus.NO_SHOW
        )).willReturn(0);

        boolean result = processor.markNoShow(
                RESERVATION_ID,
                NOW
        );

        assertThat(result).isFalse();

        verify(reservationSlotRepository, never()).deleteByReservationId(any());

        verify(doorAccessTokenService, never()).revokeByReservation(
                any(),
                any(),
                any()
        );

        verify(reservationStatusHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("자동 체크아웃에 성공하면 예약 종료 시각을 기준으로 출입 토큰을 폐기한다")
    void shouldRevokeTokenAtEndTimeWhenAutoCheckOutSucceeds() {
        given(reservationRepository.autoCheckOutIfEnded(
                RESERVATION_ID,
                NOW,
                ReservationStatus.IN_USE,
                ReservationStatus.COMPLETED
        )).willReturn(1);

        given(reservationRepository.findById(RESERVATION_ID))
                .willReturn(Optional.of(completedReservation));

        given(completedReservation.getEndTime()).willReturn(END_TIME);

        boolean result = processor.autoCheckOut(
                RESERVATION_ID,
                NOW
        );

        assertThat(result).isTrue();

        verify(doorAccessTokenService).revokeByReservation(
                RESERVATION_ID,
                END_TIME,
                "AUTO_CHECK_OUT"
        );

        verify(reservationStatusHistoryRepository).save(argThat(history ->
                history.getReservationId().equals(RESERVATION_ID)
                        && history.getChangedByMemberId() == null
                        && history.getFromStatus() == ReservationStatus.IN_USE
                        && history.getToStatus() == ReservationStatus.COMPLETED
                        && history.getReason().equals("AUTO_CHECK_OUT")
                        && history.getChangedAt().equals(END_TIME)
        ));
    }

    @Test
    @DisplayName("자동 체크아웃 상태 전이에 실패하면 출입 토큰을 폐기하지 않는다")
    void shouldNotRevokeTokenWhenAutoCheckOutTransitionFails() {
        given(reservationRepository.autoCheckOutIfEnded(
                RESERVATION_ID,
                NOW,
                ReservationStatus.IN_USE,
                ReservationStatus.COMPLETED
        )).willReturn(0);

        boolean result = processor.autoCheckOut(
                RESERVATION_ID,
                NOW
        );

        assertThat(result).isFalse();

        verify(reservationRepository, never()).findById(any());

        verify(doorAccessTokenService, never()).revokeByReservation(
                any(),
                any(),
                any()
        );

        verify(reservationStatusHistoryRepository, never()).save(any());
    }
}