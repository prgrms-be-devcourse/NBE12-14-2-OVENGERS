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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import java.time.LocalDate;

@Tag(name = "공간", description = "비회원도 이용할 수 있는 공간 및 슬롯 조회 API")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SpaceController {

    private final SpaceQueryService spaceQueryService;
    private final SpaceSlotAvailabilityService slotAvailabilityService;

    @Operation(
            summary = "공간 목록 조회",
            description = """
                공간 목록을 페이지 단위로 조회합니다.
                keyword로 검색할 수 있습니다.
                page는 0부터 시작하며, 기본 페이지 크기는 20입니다.
                """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "공간 목록 조회 성공",
                    useReturnTypeSchema = true
            )
    })
    @GetMapping("/spaces")
    public ResponseEntity<ApiResponse<PageResponse<SpaceResponse>>> getSpacesPage(
            @ParameterObject
            @PageableDefault(size = 20, direction = Sort.Direction.DESC)
            Pageable pageable,

            @Parameter(description = "공간 검색어", example = "판교")
            @RequestParam(name = "keyword", required = false)
            String keyword
    ) {
        Page<Space> spacePage = spaceQueryService.getSpacesPage(pageable, keyword);
        Page<SpaceResponse> responsePage = spacePage.map(SpaceResponse::from);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(responsePage)));
    }

    @Operation(
            summary = "공간 상세 조회",
            description = "공간의 이름, 위치, 소개, 수용 인원, 요금, 운영시간 및 이미지 경로를 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "공간 상세 조회 성공",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "SPACE_NOT_FOUND: 공간이 존재하지 않음",
                    content = @Content
            )
    })
    @GetMapping("/spaces/{spaceId}")
    public ResponseEntity<ApiResponse<SpaceDetailResponse>> getSpaceDetailById(
            @Parameter(description = "조회할 공간 ID", example = "1")
            @PathVariable("spaceId") Long spaceId) {
        Space space = spaceQueryService.getSpaceDetailById(spaceId);
        return ResponseEntity.ok(ApiResponse.success(SpaceDetailResponse.from(space)));
    }

    @Operation(
            summary = "날짜별 예약 가능 슬롯 조회",
            description = """
                지정한 공간과 날짜의 30분 단위 슬롯 및 예약 가능 여부를 조회합니다.
                isAvailable이 true이면 조회 시점에 예약 가능한 슬롯입니다.
                조회 후 다른 사용자가 선점할 수 있으므로 실제 예약 가능 여부는 예약 생성 시 다시 확인합니다.
                """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "슬롯 조회 성공",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "필수 날짜 누락 또는 날짜 형식 오류",
                    content = @Content
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "SPACE_NOT_FOUND: 공간이 존재하지 않음",
                    content = @Content
            )
    })
    @GetMapping("/spaces/{spaceId}/slots")
    public ResponseEntity<ApiResponse<SpaceSlotAvailabilityResponse>> getSlotAvailability(
            @Parameter(description = "조회할 공간 ID", example = "1")
            @PathVariable("spaceId") Long spaceId,

            @Parameter(description = "조회 날짜 (yyyy-MM-dd)", example = "2026-10-01")
            @RequestParam(name = "date")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date
    ) {
        SpaceSlotAvailabilityResponse response = slotAvailabilityService.getSlotAvailability(spaceId, date);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
