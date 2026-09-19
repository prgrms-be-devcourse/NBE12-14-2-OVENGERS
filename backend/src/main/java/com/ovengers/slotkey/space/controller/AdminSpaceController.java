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

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminSpaceController {

    private final AdminSpaceService adminSpaceService;
    private final SpaceAuthorizationService spaceAuthorizationService;

    @GetMapping("/spaces")
    public ResponseEntity<ApiResponse<PageResponse<SpaceDetailResponse>>> getSpaces(
            @RequestParam(name = "status", required = false) SpaceStatus status,
            @RequestParam(name = "keyword", required = false) String keyword,
            @PageableDefault(size = 20) Pageable pageable) {
        PageResponse<SpaceDetailResponse> response =
                PageResponse.from(adminSpaceService.getSpaces(status, keyword, pageable));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/spaces/{spaceId}")
    public ResponseEntity<ApiResponse<SpaceDetailResponse>> getSpaceDetailById(
            @PathVariable("spaceId") Long spaceId) {
        SpaceDetailResponse response = 
                SpaceDetailResponse.from(adminSpaceService.getSpaceDetailById(spaceId));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/spaces")
    public ResponseEntity<ApiResponse<SpaceDetailResponse>> createSpace(
            @Valid @RequestBody SpaceCreateRequest request,
            @AuthenticationPrincipal AuthPrincipal authPrincipal) {
        spaceAuthorizationService.validateCanManageSpace(authPrincipal);
        SpaceDetailResponse response = adminSpaceService.createSpace(request, authPrincipal.memberId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PatchMapping("/spaces/{spaceId}")
    public ResponseEntity<ApiResponse<SpaceDetailResponse>> updateSpace(
            @PathVariable("spaceId") Long spaceId,
            @Valid @RequestBody SpaceUpdateRequest request,
            @AuthenticationPrincipal AuthPrincipal authPrincipal) {
        spaceAuthorizationService.validateCanManageSpace(authPrincipal);
        SpaceDetailResponse response = adminSpaceService.updateSpace(spaceId, request, authPrincipal.memberId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
