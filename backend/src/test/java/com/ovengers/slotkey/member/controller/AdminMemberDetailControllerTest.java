
package com.ovengers.slotkey.member.controller;

import com.ovengers.slotkey.audit.entity.AuditAction;
import com.ovengers.slotkey.auth.service.AuthSessionService;
import com.ovengers.slotkey.credit.entity.CreditTransactionType;
import com.ovengers.slotkey.global.config.ClockConfig;
import com.ovengers.slotkey.global.config.SecurityConfig;
import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.global.error.GlobalExceptionHandler;
import com.ovengers.slotkey.global.security.jwt.JwtProvider;
import com.ovengers.slotkey.global.security.jwt.JwtUtil;
import com.ovengers.slotkey.member.authorization.MemberAuthorizationService;
import com.ovengers.slotkey.member.dto.response.*;
import com.ovengers.slotkey.member.entity.MemberRole;
import com.ovengers.slotkey.member.entity.MemberStatus;
import com.ovengers.slotkey.member.service.AdminMemberService;
import com.ovengers.slotkey.reservation.entity.ReservationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminMemberController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, ClockConfig.class, JwtProvider.class, MemberAuthorizationService.class, GlobalExceptionHandler.class})
@DisplayName("관리자 회원 상세 컨트롤러 테스트")
class AdminMemberDetailControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminMemberService adminMemberService;

    @MockBean
    private AuthSessionService authSessionService;

    @BeforeEach
    void allowTestSession() {
        given(authSessionService.validate(anyString(), anyLong()))
                .willReturn(AuthSessionService.ValidationResult.VALID);
    }

    @Value("${custom.jwt.secret-key}")
    private String secretKey;

    private String createToken(
            Long id,
            String email,
            MemberRole role
    ) {
        return JwtUtil.createToken(
                secretKey,
                300_000L,
                Map.of(
                        "id", id,
                        "email", email,
                        "role", role.name(),
                        "sid", "test-session"
                )
        );
    }

    private AdminMemberResponse createMemberResponse(
            Long memberId,
            String email,
            MemberStatus status
    ) {
        return new AdminMemberResponse(
                memberId,
                email,
                "테스트유저",
                MemberRole.USER,
                status,
                5000,
                LocalDateTime.of(2026, 10, 17, 10, 0),
                null,
                null
        );
    }

    @Test
    @DisplayName("회원 상세 조회: ADMIN 권한으로 조회하면 회원 정보와 요약 통계를 반환한다")
    void getMemberDetail_admin_returns200() throws Exception {
        // given
        String adminToken =
                createToken(1L, "admin@test.com", MemberRole.ADMIN);

        AdminMemberResponse member = createMemberResponse(
                42L,
                "user@test.com",
                MemberStatus.ACTIVE
        );

        AdminMemberDetailResponse response =
                new AdminMemberDetailResponse(member, 7L, 2L);

        given(adminMemberService.getMemberDetail(42L))
                .willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/admin/members/42")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.member.memberId").value(42))
                .andExpect(jsonPath("$.data.member.email").value("user@test.com"))
                .andExpect(jsonPath("$.data.member.balance").value(5000))
                .andExpect(jsonPath("$.data.totalReservationCount").value(7))
                .andExpect(jsonPath("$.data.suspensionCount").value(2));

        verify(adminMemberService).getMemberDetail(42L);
    }

    @Test
    @DisplayName("회원 상세 조회: 존재하지 않는 회원은 404 MEMBER_NOT_FOUND를 반환한다")
    void getMemberDetail_notFound_returns404() throws Exception {
        // given
        String adminToken =
                createToken(1L, "admin@test.com", MemberRole.ADMIN);

        given(adminMemberService.getMemberDetail(999L))
                .willThrow(new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        // when & then
        mockMvc.perform(get("/api/v1/admin/members/999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("FAIL"))
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));

        verify(adminMemberService).getMemberDetail(999L);
    }

    @Test
    @DisplayName("회원 상세 조회: 일반 회원은 403 ACCESS_DENIED를 반환한다")
    void getMemberDetail_userRole_returns403() throws Exception {
        // given
        String userToken =
                createToken(2L, "user@test.com", MemberRole.USER);

        // when & then
        mockMvc.perform(get("/api/v1/admin/members/42")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("FAIL"))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(adminMemberService);
    }

    @Test
    @DisplayName("회원 상세 조회: 토큰이 없으면 401 AUTHENTICATION_REQUIRED를 반환한다")
    void getMemberDetail_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/members/42"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("FAIL"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(adminMemberService);
    }

    @Test
    @DisplayName("크레딧 내역 조회: ADMIN 권한으로 조회하면 거래 내역과 페이지 정보를 반환한다")
    void getMemberCreditTransactions_admin_returns200() throws Exception {
        // given
        String adminToken =
                createToken(1L, "admin@test.com", MemberRole.ADMIN);

        Pageable pageable = PageRequest.of(1, 5);

        AdminMemberCreditTransactionResponse transaction =
                new AdminMemberCreditTransactionResponse(
                        10L,
                        CreditTransactionType.TOP_UP,
                        50000,
                        80000,
                        null,
                        LocalDateTime.of(2026, 10, 17, 15, 30)
                );

        Page<AdminMemberCreditTransactionResponse> page =
                new PageImpl<>(
                        List.of(transaction),
                        pageable,
                        6L
                );

        given(adminMemberService.getMemberCreditTransactions(
                eq(42L),
                any(Pageable.class)
        )).willReturn(page);

        // when & then
        mockMvc.perform(get("/api/v1/admin/members/42/credit-transactions")
                        .param("page", "1")
                        .param("size", "5")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.content[0].transactionId").value(10))
                .andExpect(jsonPath("$.data.content[0].type").value("TOP_UP"))
                .andExpect(jsonPath("$.data.content[0].amount").value(50000))
                .andExpect(jsonPath("$.data.content[0].balanceAfter").value(80000))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(5))
                .andExpect(jsonPath("$.data.totalElements").value(6))
                .andExpect(jsonPath("$.data.totalPages").value(2));

        ArgumentCaptor<Pageable> captor =
                ArgumentCaptor.forClass(Pageable.class);

        verify(adminMemberService)
                .getMemberCreditTransactions(eq(42L), captor.capture());

        assertThat(captor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(captor.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    @DisplayName("크레딧 내역 조회: 존재하지 않는 회원은 404 MEMBER_NOT_FOUND를 반환한다")
    void getMemberCreditTransactions_notFound_returns404() throws Exception {
        // given
        String adminToken =
                createToken(1L, "admin@test.com", MemberRole.ADMIN);

        given(adminMemberService.getMemberCreditTransactions(
                eq(999L),
                any(Pageable.class)
        )).willThrow(
                new BusinessException(ErrorCode.MEMBER_NOT_FOUND)
        );

        // when & then
        mockMvc.perform(get("/api/v1/admin/members/999/credit-transactions")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("FAIL"))
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));

        verify(adminMemberService).getMemberCreditTransactions(
                eq(999L),
                any(Pageable.class)
        );
    }

    @Test
    @DisplayName("크레딧 내역 조회: 일반 회원은 403 ACCESS_DENIED를 반환한다")
    void getMemberCreditTransactions_userRole_returns403() throws Exception {
        // given
        String userToken =
                createToken(2L, "user@test.com", MemberRole.USER);

        // when & then
        mockMvc.perform(get("/api/v1/admin/members/42/credit-transactions")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("FAIL"))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(adminMemberService);
    }

    @Test
    @DisplayName("크레딧 내역 조회: 토큰이 없으면 401 AUTHENTICATION_REQUIRED를 반환한다")
    void getMemberCreditTransactions_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/members/42/credit-transactions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("FAIL"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(adminMemberService);
    }

    @Test
    @DisplayName("예약 내역 조회: ADMIN 권한으로 조회하면 예약 정보와 페이지 정보를 반환한다")
    void getMemberReservations_admin_returns200() throws Exception {
        // given
        String adminToken =
                createToken(1L, "admin@test.com", MemberRole.ADMIN);

        Pageable pageable = PageRequest.of(1, 5);

        AdminMemberReservationResponse reservation =
                new AdminMemberReservationResponse(
                        102L,
                        10L,
                        "회의실 A",
                        LocalDateTime.of(2026, 10, 20, 14, 0),
                        LocalDateTime.of(2026, 10, 20, 15, 0),
                        ReservationStatus.CONFIRMED,
                        5000,
                        LocalDateTime.of(2026, 10, 18, 10, 0)
                );

        Page<AdminMemberReservationResponse> page =
                new PageImpl<>(
                        List.of(reservation),
                        pageable,
                        6L
                );

        given(adminMemberService.getMemberReservations(
                eq(42L),
                any(Pageable.class)
        )).willReturn(page);

        // when & then
        mockMvc.perform(get("/api/v1/admin/members/42/reservations")
                        .param("page", "1")
                        .param("size", "5")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.content[0].reservationId").value(102))
                .andExpect(jsonPath("$.data.content[0].spaceId").value(10))
                .andExpect(jsonPath("$.data.content[0].spaceName").value("회의실 A"))
                .andExpect(jsonPath("$.data.content[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.content[0].totalAmount").value(5000))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(5))
                .andExpect(jsonPath("$.data.totalElements").value(6))
                .andExpect(jsonPath("$.data.totalPages").value(2));

        ArgumentCaptor<Pageable> captor =
                ArgumentCaptor.forClass(Pageable.class);

        verify(adminMemberService)
                .getMemberReservations(eq(42L), captor.capture());

        assertThat(captor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(captor.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    @DisplayName("예약 내역 조회: 존재하지 않는 회원은 404 MEMBER_NOT_FOUND를 반환한다")
    void getMemberReservations_notFound_returns404() throws Exception {
        // given
        String adminToken =
                createToken(1L, "admin@test.com", MemberRole.ADMIN);

        given(adminMemberService.getMemberReservations(
                eq(999L),
                any(Pageable.class)
        )).willThrow(new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        // when & then
        mockMvc.perform(get("/api/v1/admin/members/999/reservations")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("FAIL"))
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));

        verify(adminMemberService).getMemberReservations(
                eq(999L),
                any(Pageable.class)
        );
    }

    @Test
    @DisplayName("예약 내역 조회: 일반 회원은 403 ACCESS_DENIED를 반환한다")
    void getMemberReservations_userRole_returns403() throws Exception {
        // given
        String userToken =
                createToken(2L, "user@test.com", MemberRole.USER);

        // when & then
        mockMvc.perform(get("/api/v1/admin/members/42/reservations")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("FAIL"))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(adminMemberService);
    }

    @Test
    @DisplayName("예약 내역 조회: 토큰이 없으면 401 AUTHENTICATION_REQUIRED를 반환한다")
    void getMemberReservations_noToken_returns401() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/admin/members/42/reservations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("FAIL"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(adminMemberService);
    }

    @Test
    @DisplayName("상태 변경 이력 조회: ADMIN 권한으로 조회하면 이력과 페이지 정보를 반환한다")
    void getMemberStatusHistory_admin_returns200() throws Exception {
        // given
        String adminToken =
                createToken(1L, "admin@test.com", MemberRole.ADMIN);

        Pageable pageable = PageRequest.of(1, 5);

        AdminMemberStatusHistoryResponse history =
                new AdminMemberStatusHistoryResponse(
                        12L,
                        AuditAction.REACTIVATE_MEMBER,
                        100L,
                        "정지 사유 해소",
                        "SUSPENDED",
                        "ACTIVE",
                        LocalDateTime.of(2026, 10, 18, 15, 0)
                );

        Page<AdminMemberStatusHistoryResponse> page =
                new PageImpl<>(
                        List.of(history),
                        pageable,
                        6L
                );

        given(adminMemberService.getMemberStatusHistory(
                eq(42L),
                any(Pageable.class)
        )).willReturn(page);

        // when & then
        mockMvc.perform(get("/api/v1/admin/members/42/status-history")
                        .param("page", "1")
                        .param("size", "5")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.content[0].historyId").value(12))
                .andExpect(jsonPath("$.data.content[0].action")
                        .value("REACTIVATE_MEMBER"))
                .andExpect(jsonPath("$.data.content[0].actorMemberId")
                        .value(100))
                .andExpect(jsonPath("$.data.content[0].reason")
                        .value("정지 사유 해소"))
                .andExpect(jsonPath("$.data.content[0].beforeValue")
                        .value("SUSPENDED"))
                .andExpect(jsonPath("$.data.content[0].afterValue")
                        .value("ACTIVE"))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(5))
                .andExpect(jsonPath("$.data.totalElements").value(6))
                .andExpect(jsonPath("$.data.totalPages").value(2));

        ArgumentCaptor<Pageable> captor =
                ArgumentCaptor.forClass(Pageable.class);

        verify(adminMemberService)
                .getMemberStatusHistory(eq(42L), captor.capture());

        assertThat(captor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(captor.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    @DisplayName("상태 변경 이력 조회: 존재하지 않는 회원은 404 MEMBER_NOT_FOUND를 반환한다")
    void getMemberStatusHistory_notFound_returns404() throws Exception {
        // given
        String adminToken =
                createToken(1L, "admin@test.com", MemberRole.ADMIN);

        given(adminMemberService.getMemberStatusHistory(
                eq(999L),
                any(Pageable.class)
        )).willThrow(
                new BusinessException(ErrorCode.MEMBER_NOT_FOUND)
        );

        // when & then
        mockMvc.perform(get("/api/v1/admin/members/999/status-history")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("FAIL"))
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));

        verify(adminMemberService).getMemberStatusHistory(
                eq(999L),
                any(Pageable.class)
        );
    }

    @Test
    @DisplayName("상태 변경 이력 조회: 일반 회원은 403 ACCESS_DENIED를 반환한다")
    void getMemberStatusHistory_userRole_returns403() throws Exception {
        // given
        String userToken =
                createToken(2L, "user@test.com", MemberRole.USER);

        // when & then
        mockMvc.perform(get("/api/v1/admin/members/42/status-history")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("FAIL"))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(adminMemberService);
    }

    @Test
    @DisplayName("상태 변경 이력 조회: 토큰이 없으면 401 AUTHENTICATION_REQUIRED를 반환한다")
    void getMemberStatusHistory_noToken_returns401() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/admin/members/42/status-history"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("FAIL"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(adminMemberService);
    }
}
