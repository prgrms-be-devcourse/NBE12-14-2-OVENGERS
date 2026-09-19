package com.ovengers.slotkey.reservation.controller;

import com.ovengers.slotkey.global.common.response.ApiResponse;
import com.ovengers.slotkey.global.security.AuthPrincipal;
import com.ovengers.slotkey.global.security.CurrentMember;
import com.ovengers.slotkey.reservation.dto.request.ReservationCreateRequest;
import com.ovengers.slotkey.reservation.dto.request.ReservationExtendRequest;
import com.ovengers.slotkey.reservation.dto.request.ReservationPayRequest;
import com.ovengers.slotkey.reservation.dto.request.ReservationSearchCondition;
import com.ovengers.slotkey.reservation.dto.response.ReservationDetailResponse;
import com.ovengers.slotkey.reservation.dto.response.ReservationResponse;
import com.ovengers.slotkey.reservation.service.ReservationCancelService;
import com.ovengers.slotkey.reservation.service.ReservationCheckOutService;
import com.ovengers.slotkey.reservation.service.ReservationExtendService;
import com.ovengers.slotkey.reservation.service.ReservationHoldService;
import com.ovengers.slotkey.reservation.service.ReservationPaymentConfirmService;
import com.ovengers.slotkey.reservation.service.ReservationQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ovengers.slotkey.reservation.dto.response.ReservationListResponse;
/**
 * 본인 예약 API. SecurityConfig에서 이 경로는 anyRequest().authenticated()로 이미
 * 인증을 강제하므로, 여기서는 소유권 검사만 신경 쓰면 된다 — 각 서비스가
 * FORBIDDEN_NOT_OWNER(403)로 이미 처리하고 있어 컨트롤러는 memberId만 그대로 넘긴다.
 */
@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationHoldService reservationHoldService;
    private final ReservationPaymentConfirmService reservationPaymentConfirmService;
    private final ReservationCancelService reservationCancelService;
    private final ReservationExtendService reservationExtendService;
    private final ReservationCheckOutService reservationCheckOutService;
    private final ReservationQueryService reservationQueryService;

    /** 예약 HOLD 생성 (결제 전 슬롯 선점, 10분간 유효). */
    @PostMapping
    public ResponseEntity<ApiResponse<ReservationResponse>> hold(
            @CurrentMember AuthPrincipal principal,
            @Valid @RequestBody ReservationCreateRequest request
    ) {
        ReservationResponse response = reservationHoldService.createHold(
                principal.memberId(),
                request.spaceId(),
                request.toStartDateTime(),
                request.toEndDateTime()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    /** 결제 확인 (HOLD -> CONFIRMED). Idempotency-Key 헤더 필수. */
    @PostMapping("/{reservationId}/pay")
    public ResponseEntity<ApiResponse<ReservationResponse>> pay(
            @CurrentMember AuthPrincipal principal,
            @PathVariable Long reservationId,
            @Valid @RequestBody ReservationPayRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        ReservationResponse response = reservationPaymentConfirmService.confirm(
                principal.memberId(), reservationId, request.spaceVersion(), idempotencyKey
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** 예약 취소 (예약자 본인만, CONFIRMED 상태에서 시작 전까지만 가능). */
    @PostMapping("/{reservationId}/cancel")
    public ResponseEntity<ApiResponse<ReservationResponse>> cancel(
            @CurrentMember AuthPrincipal principal,
            @PathVariable Long reservationId
    ) {
        ReservationResponse response = reservationCancelService.cancel(principal.memberId(), reservationId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** 예약 연장. */
    @PostMapping("/{reservationId}/extend")
    public ResponseEntity<ApiResponse<ReservationResponse>> extend(
            @CurrentMember AuthPrincipal principal,
            @PathVariable Long reservationId,
            @Valid @RequestBody ReservationExtendRequest request
    ) {
        ReservationResponse response = reservationExtendService.extend(
                principal.memberId(), reservationId, request.expectedEndTime(), request.newEndTime()
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** 체크아웃 (IN_USE -> COMPLETED, 되돌릴 수 없음). */
    @PostMapping("/{reservationId}/check-out")
    public ResponseEntity<ApiResponse<ReservationResponse>> checkOut(
            @CurrentMember AuthPrincipal principal,
            @PathVariable Long reservationId
    ) {
        ReservationResponse response = reservationCheckOutService.checkOut(principal.memberId(), reservationId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** 본인 예약 목록 (최신 시작 시각 순). */
    @GetMapping

    public ResponseEntity<ApiResponse<Page<ReservationListResponse>>> getMyReservations(
            @CurrentMember AuthPrincipal principal,
            @ModelAttribute ReservationSearchCondition condition,
            Pageable pageable
    ) {

        Page<ReservationListResponse> response =
                reservationQueryService.getMyReservations(
                        principal.memberId(),
                        condition,
                        pageable
                );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** 본인 예약 상세 + 상태 전이 이력. */
    @GetMapping("/{reservationId}")
    public ResponseEntity<ApiResponse<ReservationDetailResponse>> getMyReservation(
            @CurrentMember AuthPrincipal principal,
            @PathVariable Long reservationId
    ) {
        ReservationDetailResponse response =
                reservationQueryService.getMyReservation(principal.memberId(), reservationId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
