package com.ovengers.slotkey.space.service;

import com.ovengers.slotkey.audit.entity.AuditAction;
import com.ovengers.slotkey.audit.entity.AuditTargetType;
import com.ovengers.slotkey.audit.service.AuditLogService;
import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.space.dto.request.SpaceCreateRequest;
import com.ovengers.slotkey.space.dto.request.SpaceUpdateRequest;
import com.ovengers.slotkey.space.dto.response.SpaceDetailResponse;
import com.ovengers.slotkey.space.entity.Space;
import com.ovengers.slotkey.space.repository.SpaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminSpaceService {

    private final SpaceRepository spaceRepository;
    private final AuditLogService auditLogService;

    @Transactional
    public SpaceDetailResponse createSpace(SpaceCreateRequest request, Long adminMemberId) {
        Space space = request.toEntity();
        Space savedSpace = spaceRepository.save(space);

        SpaceDetailResponse response = SpaceDetailResponse.from(savedSpace);

        // 감사 로그 기록
        auditLogService.log(
                adminMemberId,
                AuditAction.REGISTER_SPACE,
                AuditTargetType.SPACE,
                savedSpace.getId(),
                null,
                response);

        return response;
    }

    @Transactional
    public SpaceDetailResponse updateSpace(Long spaceId, SpaceUpdateRequest request, Long adminMemberId) {
        Space space = spaceRepository.findById(spaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SPACE_NOT_FOUND));

        SpaceDetailResponse beforeSnapshot = SpaceDetailResponse.from(space);

        space.updateDetail(request);

        SpaceDetailResponse afterSnapshot = SpaceDetailResponse.from(space);

        // 감사 로그 기록 (수정 전후 스냅샷 비교)
        auditLogService.log(
                adminMemberId,
                AuditAction.MODIFY_SPACE,
                AuditTargetType.SPACE,
                space.getId(),
                beforeSnapshot,
                afterSnapshot);

        return afterSnapshot;
    }
}
