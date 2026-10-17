
package com.ovengers.slotkey.member.controller;

import com.ovengers.slotkey.credit.entity.CreditTransactionType;
import com.ovengers.slotkey.global.config.SecurityConfig;
import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.global.error.GlobalExceptionHandler;
import com.ovengers.slotkey.global.security.jwt.JwtProvider;
import com.ovengers.slotkey.global.security.jwt.JwtUtil;
import com.ovengers.slotkey.member.authorization.MemberAuthorizationService;
import com.ovengers.slotkey.member.dto.response.AdminMemberCreditTransactionResponse;
import com.ovengers.slotkey.member.dto.response.AdminMemberDetailResponse;
import com.ovengers.slotkey.member.dto.response.AdminMemberResponse;
import com.ovengers.slotkey.member.entity.MemberRole;
import com.ovengers.slotkey.member.entity.MemberStatus;
import com.ovengers.slotkey.member.service.AdminMemberService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminMemberController.class)
@ActiveProfiles("test")
@Import({
        SecurityConfig.class,
        JwtProvider.class,
        MemberAuthorizationService.class,
        GlobalExceptionHandler.class
})
@DisplayName("관리자 회원 상세 컨트롤러 테스트")
class AdminMemberDetailControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminMemberService adminMemberService;

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
                        "role", role.name()
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
}
