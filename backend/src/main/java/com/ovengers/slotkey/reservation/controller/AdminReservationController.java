package com.ovengers.slotkey.reservation.controller;

import com.ovengers.slotkey.global.common.response.ApiResponse;
import com.ovengers.slotkey.global.security.AuthPrincipal;
import com.ovengers.slotkey.global.security.CurrentMember;
import com.ovengers.slotkey.reservation.dto.request.ForceCancelRequest;
import com.ovengers.slotkey.reservation.dto.response.AdminReservationDetailResponse;
import com.ovengers.slotkey.reservation.dto.response.AdminReservationResponse;
import com.ovengers.slotkey.reservation.service.AdminReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 예약 조회/강제취소 API. SecurityConfig의 "/api/v1/admin/**" -> hasRole("ADMIN")
 * 매처로 접근이 이미 제한되므로 이 컨트롤러는 별도 역할 검사를 하지 않는다.
 */
@RestController
@RequestMapping("/api/v1/admin/reservations")
@RequiredArgsConstructor
public class AdminReservationController {

    private final AdminReservationService adminReservationService;

    /** 전체 예약 목록 (취소된 예약 포함). */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<AdminReservationResponse>>> getReservations(Pageable pageable) {
        Page<AdminReservationResponse> response = adminReservationService.findAllReservations(pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** 예약 상세 (상태 이력 + 출입 로그 포함). */
    @GetMapping("/{reservationId}")
    public ResponseEntity<ApiResponse<AdminReservationDetailResponse>> getReservation(
            @PathVariable Long reservationId
    ) {
        AdminReservationDetailResponse response = adminReservationService.getReservationDetail(reservationId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** 강제 취소 (상태·시간 무관, 사유 기록 필수). */
    @PostMapping("/{reservationId}/force-cancel")
    public ResponseEntity<ApiResponse<AdminReservationResponse>> forceCancel(
            @CurrentMember AuthPrincipal principal,
            @PathVariable Long reservationId,
            @Valid @RequestBody ForceCancelRequest request
    ) {
        AdminReservationResponse response =
                adminReservationService.forceCancel(reservationId, request.reason(), principal.memberId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
