package com.ovengers.slotkey.stats.service;

import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.reservation.entity.ReservationStatus;
import com.ovengers.slotkey.stats.dto.response.StatusCountsResponse;
import com.ovengers.slotkey.stats.repository.ReservationStatusCountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

/** 운영 대시보드의 "날짜별 상태별 예약 건수". 브라우저가 예약을 내려받아 세던 방식을 서버 집계로 옮긴 것이다. */
@Service
@Transactional(readOnly = true)
public class ReservationStatusCountService {

    private final ReservationStatusCountRepository repository;
    private final Clock clock;

    public ReservationStatusCountService(ReservationStatusCountRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** @param date 생략하면 오늘(KST). @param spaceId 생략하면 전체 공간 */
    public StatusCountsResponse count(LocalDate date, Long spaceId) {
        if (spaceId != null && spaceId < 1) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "spaceId는 양수여야 합니다.");
        }
        LocalDate target = date != null ? date : LocalDate.now(clock);
        Map<ReservationStatus, Long> found =
                repository.countByStatus(target.atStartOfDay(), target.plusDays(1).atStartOfDay(), spaceId);

        Map<ReservationStatus, Long> counts = new EnumMap<>(ReservationStatus.class);
        for (ReservationStatus status : ReservationStatus.values()) {
            counts.put(status, found.getOrDefault(status, 0L));
        }
        return new StatusCountsResponse(target, counts);
    }
}
