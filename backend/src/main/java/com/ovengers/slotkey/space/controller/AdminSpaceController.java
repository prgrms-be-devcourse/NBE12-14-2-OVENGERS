package com.ovengers.slotkey.space.controller;

import com.ovengers.slotkey.global.common.response.ApiResponse;
import com.ovengers.slotkey.space.dto.request.SpaceCreateRequest;
import com.ovengers.slotkey.space.dto.request.SpaceUpdateRequest;
import com.ovengers.slotkey.space.dto.response.SpaceDetailResponse;
import com.ovengers.slotkey.space.service.AdminSpaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminSpaceController {

    private final AdminSpaceService adminSpaceService;

    @PostMapping("/spaces")
    public ResponseEntity<ApiResponse<SpaceDetailResponse>> createSpace(
            @Valid @RequestBody SpaceCreateRequest request,
            @RequestHeader(value = "X-Actor-Member-Id", required = false) Long actorMemberId) {
        SpaceDetailResponse response = adminSpaceService.createSpace(request, actorMemberId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PatchMapping("/spaces/{spaceId}")
    public ResponseEntity<ApiResponse<SpaceDetailResponse>> updateSpace(
            @PathVariable Long spaceId,
            @Valid @RequestBody SpaceUpdateRequest request,
            @RequestHeader(value = "X-Actor-Member-Id", required = false) Long actorMemberId) {
        SpaceDetailResponse response = adminSpaceService.updateSpace(spaceId, request, actorMemberId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
