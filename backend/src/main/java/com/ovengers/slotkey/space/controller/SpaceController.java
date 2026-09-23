package com.ovengers.slotkey.space.controller;

import com.ovengers.slotkey.global.common.response.ApiResponse;
import com.ovengers.slotkey.global.common.response.PageResponse;
import com.ovengers.slotkey.space.dto.request.SpaceSearchCondition;
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
import java.time.LocalTime;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SpaceController {

    private final SpaceQueryService spaceQueryService;
    private final SpaceSlotAvailabilityService slotAvailabilityService;

    /**
     * 오피스 찾기 목록. location/minPrice/maxPrice는 단순 필터, date+startTime+endTime을
     * 모두 지정하면 그 시간대에 예약 가능한(점유된 슬롯이 없는) 공간만 반환한다(참고용 스냅샷).
     */
    @GetMapping("/spaces")
    public ResponseEntity<ApiResponse<PageResponse<SpaceResponse>>> getSpacesPage(
            @PageableDefault(size = 20, direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "location", required = false) String location,
            @RequestParam(name = "minPrice", required = false) Long minPrice,
            @RequestParam(name = "maxPrice", required = false) Long maxPrice,
            @RequestParam(name = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(name = "startTime", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startTime,
            @RequestParam(name = "endTime", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime endTime) {
        SpaceSearchCondition condition =
                new SpaceSearchCondition(keyword, location, minPrice, maxPrice, date, startTime, endTime);
        Page<Space> spacePage = spaceQueryService.getSpacesPage(pageable, condition);
        Page<SpaceResponse> responsePage = spacePage.map(SpaceResponse::from);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(responsePage)));
    }

    @GetMapping("/spaces/{spaceId}")
    public ResponseEntity<ApiResponse<SpaceDetailResponse>> getSpaceDetailById(
            @PathVariable("spaceId") Long spaceId) {
        Space space = spaceQueryService.getSpaceDetailById(spaceId);
        return ResponseEntity.ok(ApiResponse.success(SpaceDetailResponse.from(space)));
    }

    @GetMapping("/spaces/{spaceId}/slots")
    public ResponseEntity<ApiResponse<SpaceSlotAvailabilityResponse>> getSlotAvailability(
            @PathVariable("spaceId") Long spaceId,
            @RequestParam(name = "date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        SpaceSlotAvailabilityResponse response = slotAvailabilityService.getSlotAvailability(spaceId, date);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
