package com.ovengers.slotkey.reservation.service;

import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.reservation.dto.response.ReservationStatusHistoryResponse;
import com.ovengers.slotkey.reservation.entity.Reservation;
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

    // 상태 변경 이력 저장
    @Transactional
    public ReservationStatusHistoryResponse create(
            Reservation reservation,
            Member changedByMember,
            ReservationStatus fromStatus,
            ReservationStatus toStatus,
            String reason,
            LocalDateTime changedAt
    ) {
        //  상태 변경 이력 생성
        ReservationStatusHistory history = new ReservationStatusHistory(
                reservation,
                changedByMember,
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
        //  해당 예약의 이력을 시간순으로 조회
        List<ReservationStatusHistory> histories =
                reservationStatusHistoryRepository
                        .findAllByReservation_IdOrderByChangedAtAsc(reservationId);

        List<ReservationStatusHistoryResponse> responses = new ArrayList<>();

        for (ReservationStatusHistory history : histories) {
            responses.add(ReservationStatusHistoryResponse.from(history));
        }

        return responses;
    }
}