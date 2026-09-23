package com.ovengers.slotkey.space.controller;

import com.ovengers.slotkey.global.common.response.ApiResponse;
import com.ovengers.slotkey.global.common.response.PageResponse;
import com.ovengers.slotkey.global.security.AuthPrincipal;
import com.ovengers.slotkey.space.authorization.SpaceAuthorizationService;
import com.ovengers.slotkey.space.dto.request.SpaceCreateRequest;
import com.ovengers.slotkey.space.dto.request.SpaceUpdateRequest;
import com.ovengers.slotkey.space.dto.response.SpaceDetailResponse;
import com.ovengers.slotkey.space.entity.SpaceStatus;
import com.ovengers.slotkey.space.service.AdminSpaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;

@Tag(name = "관리자 - 공간", description = "관리자 공간 조회, 등록 및 수정 API")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증 정보가 없거나 액세스 토큰이 유효하지 않음",
                content = @Content
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "관리자 권한이 없음",
                content = @Content
        )
})
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminSpaceController {

    private final AdminSpaceService adminSpaceService;
    private final SpaceAuthorizationService spaceAuthorizationService;
    private final com.ovengers.slotkey.space.service.SpaceImageService spaceImageService;

    @Operation(
            summary = "관리자 공간 목록 조회",
            description = """
                공간 상태와 검색어로 공간 목록을 조회합니다.
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
    public ResponseEntity<ApiResponse<PageResponse<SpaceDetailResponse>>> getSpaces(
            @Parameter(description = "공간 상태 필터. 생략하면 상태로 제한하지 않음")
            @RequestParam(name = "status", required = false) SpaceStatus status,

            @Parameter(description = "공간 검색어", example = "판교")
            @RequestParam(name = "keyword", required = false) String keyword,

            @ParameterObject
            @PageableDefault(size = 20) Pageable pageable) {
        PageResponse<SpaceDetailResponse> response =
                PageResponse.from(adminSpaceService.getSpaces(status, keyword, pageable));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
            summary = "관리자 공간 상세 조회",
            description = "관리자가 수정할 공간의 상세 정보를 조회합니다."
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
            @PathVariable("spaceId") Long spaceId) {
        SpaceDetailResponse response = 
                SpaceDetailResponse.from(adminSpaceService.getSpaceDetailById(spaceId));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
            summary = "공간 등록",
            description = """
                새로운 공간을 등록합니다.
                슬롯당 요금은 100원 이상이며 100원 단위여야 합니다.
                운영 종료 시각은 운영 시작 시각보다 늦어야 합니다.
                imagePath에는 이미지 주소를 입력합니다. 파일 업로드 API가 아닙니다.
                """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "공간 등록 성공",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = """
                        VALIDATION_FAILED: 입력값 검증 실패.
                        INVALID_PRICE_UNIT: 요금 단위 오류.
                        INVALID_OPERATING_HOURS: 운영시간 오류.
                        """,
                    content = @Content
            )
    })
    @PostMapping("/spaces")
    public ResponseEntity<ApiResponse<SpaceDetailResponse>> createSpace(
            @Valid @RequestBody SpaceCreateRequest request,
            @AuthenticationPrincipal AuthPrincipal authPrincipal) {
        spaceAuthorizationService.validateCanManageSpace(authPrincipal);
        SpaceDetailResponse response = adminSpaceService.createSpace(request, authPrincipal.memberId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @Operation(
            summary = "공간 수정",
            description = """
                경로의 spaceId에 해당하는 공간 정보를 수정합니다.
                변경할 항목만 전달하며, null인 항목은 기존 값을 유지합니다.
                기존 예약과 충돌하는 운영시간 변경은 거절됩니다.
                """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "공간 수정 성공",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "입력값 검증 실패 또는 요금·운영시간 오류",
                    content = @Content
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "SPACE_NOT_FOUND: 공간이 존재하지 않음",
                    content = @Content
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "SPACE_OPERATING_HOURS_CONFLICT: 변경할 운영시간이 기존 예약과 충돌함",
                    content = @Content
            )
    })
    @PatchMapping("/spaces/{spaceId}")
    public ResponseEntity<ApiResponse<SpaceDetailResponse>> updateSpace(
            @Parameter(description = "대상 공간 ID", example = "1")
            @PathVariable("spaceId") Long spaceId,
            @Valid @RequestBody SpaceUpdateRequest request,
            @AuthenticationPrincipal AuthPrincipal authPrincipal) {
        spaceAuthorizationService.validateCanManageSpace(authPrincipal);
        SpaceDetailResponse response = adminSpaceService.updateSpace(spaceId, request, authPrincipal.memberId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping(value = "/spaces/{spaceId}/image", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<SpaceDetailResponse>> uploadSpaceImage(
            @PathVariable("spaceId") Long spaceId,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @AuthenticationPrincipal AuthPrincipal authPrincipal) {
        spaceAuthorizationService.validateCanManageSpace(authPrincipal);
        SpaceDetailResponse response = spaceImageService.uploadAndAttachSpaceImage(spaceId, file, authPrincipal.memberId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
