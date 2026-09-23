package com.ovengers.slotkey.space.service;

import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.space.dto.request.SpaceSearchCondition;
import com.ovengers.slotkey.space.entity.Space;
import com.ovengers.slotkey.space.entity.SpaceStatus;
import com.ovengers.slotkey.space.repository.SpaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpaceQueryService {

    /**
     * 어떤 공간 id와도 매치되지 않는 sentinel 값. "AND s.id NOT IN :excludedSpaceIds" 조건을
     * 항상 동일한 쿼리로 유지하기 위해, 제외할 공간이 없을 때도 빈 컬렉션 대신 이 값을 바인딩한다.
     */
    private static final List<Long> NO_EXCLUSION = List.of(-1L);

    private final SpaceRepository spaceRepository;
    private final OccupiedSlotProvider occupiedSlotProvider;
    private final Clock clock;

    /**
     * 오피스 찾기 목록 조회. keyword/location/가격 범위는 단순 WHERE 조건이고,
     * date+startTime+endTime을 모두 지정하면 그 시간대에 이미 점유된 슬롯이 있는 공간을 제외한다
     * (reservation_slot 조회 1건 + space 조회 1건, 총 2개 쿼리로 처리 — space 도메인은
     * OccupiedSlotProvider 포트를 통해서만 예약 도메인과 통신한다).
     */
    public Page<Space> getSpacesPage(Pageable pageable, SpaceSearchCondition condition) {
        List<Long> excludedSpaceIds = NO_EXCLUSION;

        if (condition.hasTimeFilter()) {
            LocalDateTime start = LocalDateTime.of(condition.date(), condition.startTime());
            LocalDateTime end = LocalDateTime.of(condition.date(), condition.endTime());
            if (!start.isBefore(end)) {
                throw new BusinessException(ErrorCode.INVALID_TIME_RANGE);
            }

            Set<Long> occupiedSpaceIds = occupiedSlotProvider.getOccupiedSpaceIds(
                    start, end, LocalDateTime.now(clock));
            if (!occupiedSpaceIds.isEmpty()) {
                excludedSpaceIds = List.copyOf(occupiedSpaceIds);
            }
        }

        return spaceRepository.searchSpaces(
                SpaceStatus.ACTIVE,
                condition.keyword(),
                condition.location(),
                condition.minPrice(),
                condition.maxPrice(),
                excludedSpaceIds,
                pageable);
    }

    public Space getSpaceDetailById(Long spaceId) {
        return spaceRepository.findById(spaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SPACE_NOT_FOUND));
    }
}
