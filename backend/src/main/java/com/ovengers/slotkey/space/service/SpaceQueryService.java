package com.ovengers.slotkey.space.service;

import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.space.entity.Space;
import com.ovengers.slotkey.space.entity.SpaceStatus;
import com.ovengers.slotkey.space.repository.SpaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpaceQueryService {

    private final SpaceRepository spaceRepository;

    public Page<Space> getSpacesPage(Pageable pageable, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return spaceRepository.findAllByStatus(SpaceStatus.ACTIVE, pageable);
        }
        return spaceRepository.searchSpacesByNameOrDescription(
                keyword, SpaceStatus.ACTIVE, pageable);
    }

    public Space getSpaceDetailById(Long spaceId) {
        return spaceRepository.findById(spaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SPACE_NOT_FOUND));
    }
}
