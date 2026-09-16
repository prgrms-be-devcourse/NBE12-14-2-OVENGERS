package com.ovengers.slotkey.reservation.service;

import com.ovengers.slotkey.reservation.dto.response.ReservationStatusHistoryResponse;
import com.ovengers.slotkey.reservation.entity.ReservationStatus;
import com.ovengers.slotkey.reservation.entity.ReservationStatusHistory;
import com.ovengers.slotkey.reservation.repository.ReservationStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationStatusHistoryService {

    private final ReservationStatusHistoryRepository reservationStatusHistoryRepository;

    // 예약 상태 변경 이력 저장
    @Transactional
    public ReservationStatusHistoryResponse create(
            Long reservationId,
            Long changedByMemberId,
            ReservationStatus fromStatus,
            ReservationStatus toStatus,
            String reason,
            LocalDateTime changedAt
    ) {
        ReservationStatusHistory history =
                ReservationStatusHistory.of(
                        reservationId,
                        changedByMemberId,
                        fromStatus,
                        toStatus,
                        reason,
                        changedAt
                );

        ReservationStatusHistory savedHistory =
                reservationStatusHistoryRepository.save(history);

        return ReservationStatusHistoryResponse.from(savedHistory);
    }

    // 예약 ID 기준 상태 변경 이력 조회
    public List<ReservationStatusHistoryResponse> findAllByReservationId(
            Long reservationId
    ) {
        List<ReservationStatusHistory> histories =
                reservationStatusHistoryRepository
                        .findAllByReservationIdOrderByChangedAtAsc(reservationId);

        List<ReservationStatusHistoryResponse> responses = new ArrayList<>();

        for (ReservationStatusHistory history : histories) {
            responses.add(ReservationStatusHistoryResponse.from(history));
        }

        return responses;
    }
}