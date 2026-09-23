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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
/**
 * 관리자 예약 조회/강제취소 API. SecurityConfig의 "/api/v1/admin/**" -> hasRole("ADMIN")
 * 매처로 접근이 이미 제한되므로 이 컨트롤러는 별도 역할 검사를 하지 않는다.
 */
@Tag(name = "관리자 - 예약", description = "전체 예약 조회 및 관리자 강제 취소 API")
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
@RequestMapping("/api/v1/admin/reservations")
@RequiredArgsConstructor
public class AdminReservationController {

    private final AdminReservationService adminReservationService;

    /** 전체 예약 목록 (취소된 예약 포함). */
    @Operation(
            summary = "관리자 예약 목록 조회",
            description = "취소된 예약을 포함한 전체 회원의 예약을 페이지 단위로 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "예약 목록 조회 성공",
                    useReturnTypeSchema = true
            )
    })
    @GetMapping
    public ResponseEntity<ApiResponse<Page<AdminReservationResponse>>> getReservations(@ParameterObject Pageable pageable) {
        Page<AdminReservationResponse> response = adminReservationService.findAllReservations(pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** 예약 상세 (상태 이력 + 출입 로그 포함). */
    @Operation(
            summary = "관리자 예약 상세 조회",
            description = "대상 예약의 회원·공간 정보, 상태 변경 이력 및 출입 기록을 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "예약 상세 조회 성공",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "RESERVATION_NOT_FOUND: 예약이 존재하지 않음",
                    content = @Content
            )
    })
    @GetMapping("/{reservationId}")
    public ResponseEntity<ApiResponse<AdminReservationDetailResponse>> getReservation(
            @PathVariable Long reservationId
    ) {
        AdminReservationDetailResponse response = adminReservationService.getReservationDetail(reservationId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** 강제 취소 (상태·시간 무관, 사유 기록 필수). */
    @Operation(
            summary = "관리자 예약 강제 취소",
            description = """
                관리자 권한으로 대상 예약을 강제 취소합니다.
                요청 본문에 취소 사유를 입력해야 합니다.
                일반 사용자 취소 API와 구분되는 관리자 전용 기능입니다.
                """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "강제 취소 처리 결과 반환",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "VALIDATION_FAILED: 취소 사유 등 요청값 검증 실패",
                    content = @Content
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "RESERVATION_NOT_FOUND: 예약이 존재하지 않음",
                    content = @Content
            )
    })
    @PostMapping("/{reservationId}/force-cancel")
    public ResponseEntity<ApiResponse<AdminReservationResponse>> forceCancel(
            @Parameter(hidden = true)
            @CurrentMember AuthPrincipal principal,
            @Parameter(description = "대상 예약 ID", example = "1")
            @PathVariable Long reservationId,
            @Valid @RequestBody ForceCancelRequest request
    ) {
        AdminReservationResponse response =
                adminReservationService.forceCancel(reservationId, request.reason(), principal.memberId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
