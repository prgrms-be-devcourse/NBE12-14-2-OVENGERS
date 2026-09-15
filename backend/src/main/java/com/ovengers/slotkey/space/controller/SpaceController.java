package com.ovengers.slotkey.space.controller;

import com.ovengers.slotkey.global.common.response.ApiResponse;
import com.ovengers.slotkey.global.common.response.PageResponse;
import com.ovengers.slotkey.space.dto.response.SpaceDetailResponse;
import com.ovengers.slotkey.space.dto.response.SpaceResponse;
import com.ovengers.slotkey.space.dto.response.SpaceSlotAvailabilityResponse;
import com.ovengers.slotkey.space.entity.Space;
import com.ovengers.slotkey.space.service.SpaceQueryService;
import com.ovengers.slotkey.space.service.SpaceSlotAvailabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SpaceController {

    private final SpaceQueryService spaceQueryService;
    private final SpaceSlotAvailabilityService slotAvailabilityService;

    @GetMapping("/spaces")
    public ResponseEntity<ApiResponse<PageResponse<SpaceResponse>>> getSpacesPage(
            @PageableDefault(size = 20, direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String keyword) {
        Page<Space> spacePage = spaceQueryService.getSpacesPage(pageable, keyword);
        Page<SpaceResponse> responsePage = spacePage.map(SpaceResponse::from);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(responsePage)));
    }

    @GetMapping("/spaces/{spaceId}")
    public ResponseEntity<ApiResponse<SpaceDetailResponse>> getSpaceDetailById(
            @PathVariable Long spaceId) {
        Space space = spaceQueryService.getSpaceDetailById(spaceId);
        return ResponseEntity.ok(ApiResponse.success(SpaceDetailResponse.from(space)));
    }

    @GetMapping("/spaces/{spaceId}/slots")
    public ResponseEntity<ApiResponse<SpaceSlotAvailabilityResponse>> getSlotAvailability(
            @PathVariable Long spaceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        SpaceSlotAvailabilityResponse response = slotAvailabilityService.getSlotAvailability(spaceId, date);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
