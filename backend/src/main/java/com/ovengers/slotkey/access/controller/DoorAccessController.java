package com.ovengers.slotkey.access.controller;

import com.ovengers.slotkey.access.dto.request.DoorAccessVerifyRequest;
import com.ovengers.slotkey.access.dto.response.DoorAccessLogResponse;
import com.ovengers.slotkey.access.dto.response.DoorAccessTokenResponse;
import com.ovengers.slotkey.access.dto.response.DoorAccessVerifyResponse;
import com.ovengers.slotkey.access.service.DoorAccessLogService;
import com.ovengers.slotkey.access.service.DoorAccessTokenService;
import com.ovengers.slotkey.access.service.DoorAccessVerificationService;
import com.ovengers.slotkey.global.common.response.ApiResponse;
import com.ovengers.slotkey.global.security.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class DoorAccessController {

    private final DoorAccessTokenService doorAccessTokenService;
    private final DoorAccessLogService doorAccessLogService;
    private final DoorAccessVerificationService doorAccessVerificationService;
    private final Clock clock;

    // 예약에 사용할 출입 토큰 발급
    @PostMapping("/reservations/{reservationId}/door-token")
    public ResponseEntity<ApiResponse<DoorAccessTokenResponse>> issueToken(
            @PathVariable Long reservationId,
            @AuthenticationPrincipal AuthPrincipal authPrincipal
    ) {
        DoorAccessTokenResponse response =
                doorAccessTokenService.issue(
                        authPrincipal.memberId(),
                        reservationId
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    // 예약에 발급된 출입 토큰 폐기
    @PatchMapping("/reservations/{reservationId}/access-token/revoke")
    public ResponseEntity<ApiResponse<Void>> revokeToken(
            @PathVariable Long reservationId,
            @AuthenticationPrincipal AuthPrincipal authPrincipal
    ) {
        doorAccessTokenService.revokeByReservationId(
                authPrincipal.memberId(),
                reservationId,
                LocalDateTime.now(clock),
                "사용자 요청"
        );

        return ResponseEntity.ok(
                ApiResponse.success()
        );
    }

    // 예약별 출입 기록 조회
    @GetMapping("/reservations/{reservationId}/access-logs")
    public ResponseEntity<ApiResponse<List<DoorAccessLogResponse>>> findAccessLogs(
            @PathVariable Long reservationId,
            @AuthenticationPrincipal AuthPrincipal authPrincipal
    ) {
        List<DoorAccessLogResponse> responses =
                doorAccessLogService.findResponsesByReservationId(
                        authPrincipal.memberId(),
                        reservationId
                );

        return ResponseEntity.ok(
                ApiResponse.success(responses)
        );
    }

    // 제출된 출입 토큰 검증
    @PostMapping("/door-access/verify")
    public ResponseEntity<ApiResponse<DoorAccessVerifyResponse>> verify(
            @AuthenticationPrincipal AuthPrincipal authPrincipal,
            @Valid @RequestBody DoorAccessVerifyRequest request
    ) {
        DoorAccessVerifyResponse response =
                doorAccessVerificationService.verify(
                        authPrincipal.memberId(),
                        request
                );

        return ResponseEntity.ok(
                ApiResponse.success(response)
        );
    }
}