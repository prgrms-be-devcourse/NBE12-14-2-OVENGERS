package com.ovengers.slotkey.member.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ovengers.slotkey.global.config.SecurityConfig;
import com.ovengers.slotkey.global.error.GlobalExceptionHandler;
import com.ovengers.slotkey.global.security.jwt.JwtProvider;
import com.ovengers.slotkey.global.security.jwt.JwtUtil;
import com.ovengers.slotkey.member.authorization.MemberAuthorizationService;
import com.ovengers.slotkey.member.dto.response.AdminMemberResponse;
import com.ovengers.slotkey.member.dto.response.MemberStatusChangeResponse;
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
import org.springframework.http.MediaType;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminMemberController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, JwtProvider.class, MemberAuthorizationService.class, GlobalExceptionHandler.class})
@DisplayName("관리자 회원 컨트롤러 웹 계층 테스트")
class AdminMemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AdminMemberService adminMemberService;

    @Value("${custom.jwt.secret-key}")
    private String secretKey;

    private String createToken(Long id, String email, MemberRole role) {
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

    private AdminMemberResponse createMemberResponse(Long memberId, String email, MemberStatus status) {
        return new AdminMemberResponse(
                memberId,
                email,
                "테스트유저",
                MemberRole.USER,
                status,
                5000,
                LocalDateTime.now(),
                null,
                null
        );
    }

    @Test
    @DisplayName("목록 조회: ADMIN 권한 토큰은 200 OK를 반환한다")
    void getMembers_adminRole_returns200() throws Exception {
        String adminToken = createToken(1L, "admin@test.com", MemberRole.ADMIN);
        Page<AdminMemberResponse> page = new PageImpl<>(List.of(createMemberResponse(10L, "u10@test.com", MemberStatus.ACTIVE)));
        given(adminMemberService.getMembers(any(), any(), any())).willReturn(page);

        mockMvc.perform(get("/api/v1/admin/members")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.code").value("OK"));
    }

    @Test
    @DisplayName("목록 조회: USER 권한 토큰은 403 ACCESS_DENIED를 반환하고 서비스를 호출하지 않는다")
    void getMembers_userRole_returns403() throws Exception {
        String userToken = createToken(2L, "user@test.com", MemberRole.USER);

        mockMvc.perform(get("/api/v1/admin/members")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("FAIL"))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(adminMemberService);
    }

    @Test
    @DisplayName("목록 조회: 토큰 없음은 401 AUTHENTICATION_REQUIRED를 반환하고 서비스를 호출하지 않는다")
    void getMembers_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/members"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("FAIL"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(adminMemberService);
    }

    @Test
    @DisplayName("목록 조회: 잘못된 토큰은 401 INVALID_ACCESS_TOKEN을 반환하고 서비스를 호출하지 않는다")
    void getMembers_invalidToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/members")
                        .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("FAIL"))
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));

        verifyNoInteractions(adminMemberService);
    }

    @Test
    @DisplayName("목록 조회: 기본 요청(?page=0&size=20)은 status=null, keyword=null 및 페이지 메타데이터를 반환한다")
    void getMembers_defaultRequest_passesNullFiltersAndReturnsPageResponse() throws Exception {
        String adminToken = createToken(1L, "admin@test.com", MemberRole.ADMIN);
        Pageable pageable = PageRequest.of(0, 20);
        Page<AdminMemberResponse> page = new PageImpl<>(
                List.of(createMemberResponse(10L, "u10@test.com", MemberStatus.ACTIVE)),
                pageable,
                1L
        );
        given(adminMemberService.getMembers(eq(null), eq(null), any(Pageable.class))).willReturn(page);

        mockMvc.perform(get("/api/v1/admin/members?page=0&size=20")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content[0].memberId").value(10L))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(adminMemberService).getMembers(eq(null), eq(null), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(0);
        assertThat(captor.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("목록 조회: 필터 요청(?status=ACTIVE&keyword=sample&page=1&size=5)이 서비스에 올바르게 전달된다")
    void getMembers_filterRequest_passesFiltersCorrectly() throws Exception {
        String adminToken = createToken(1L, "admin@test.com", MemberRole.ADMIN);
        Pageable pageable = PageRequest.of(1, 5);
        Page<AdminMemberResponse> page = new PageImpl<>(List.of(), pageable, 0L);
        given(adminMemberService.getMembers(eq(MemberStatus.ACTIVE), eq("sample"), any(Pageable.class))).willReturn(page);

        mockMvc.perform(get("/api/v1/admin/members?status=ACTIVE&keyword=sample&page=1&size=5")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(5));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(adminMemberService).getMembers(eq(MemberStatus.ACTIVE), eq("sample"), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(captor.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    @DisplayName("목록 조회: 페이지 파라미터 생략 시 기본값 page 0, size 20이 전달된다")
    void getMembers_omittedPaging_usesDefaultPageAndSize() throws Exception {
        String adminToken = createToken(1L, "admin@test.com", MemberRole.ADMIN);
        given(adminMemberService.getMembers(eq(null), eq(null), any(Pageable.class)))
                .willReturn(Page.empty());

        mockMvc.perform(get("/api/v1/admin/members")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(adminMemberService).getMembers(eq(null), eq(null), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(0);
        assertThat(captor.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("목록 조회: 빈 Page 반환 시 HTTP 200과 빈 content 배열을 반환한다")
    void getMembers_emptyPage_returns200WithEmptyContent() throws Exception {
        String adminToken = createToken(1L, "admin@test.com", MemberRole.ADMIN);
        given(adminMemberService.getMembers(eq(null), eq(null), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0L));

        mockMvc.perform(get("/api/v1/admin/members")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("회원 정지: memberId 42L, 사유, 토큰의 관리자 ID가 서비스에 올바르게 전달된다")
    void suspend_passesPathAndBodyCorrectly() throws Exception {
        String adminToken = createToken(1L, "admin@test.com", MemberRole.ADMIN);
        MemberStatusChangeResponse response = new MemberStatusChangeResponse(
                42L, MemberStatus.SUSPENDED, "바인딩 검증", LocalDateTime.now()
        );
        given(adminMemberService.suspend(eq(42L), eq("바인딩 검증"), eq(1L))).willReturn(response);

        mockMvc.perform(patch("/api/v1/admin/members/42/suspend")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"바인딩 검증\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberId").value(42L))
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"));

        verify(adminMemberService).suspend(42L, "바인딩 검증", 1L);
    }

    @Test
    @DisplayName("회원 복구: memberId 42L, 사유, 토큰의 관리자 ID가 서비스에 올바르게 전달된다")
    void restore_passesPathAndBodyCorrectly() throws Exception {
        String adminToken = createToken(1L, "admin@test.com", MemberRole.ADMIN);
        MemberStatusChangeResponse response = new MemberStatusChangeResponse(
                42L, MemberStatus.ACTIVE, "바인딩 검증", LocalDateTime.now()
        );
        given(adminMemberService.restore(eq(42L), eq("바인딩 검증"), eq(1L))).willReturn(response);

        mockMvc.perform(patch("/api/v1/admin/members/42/restore")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"바인딩 검증\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberId").value(42L))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        verify(adminMemberService).restore(42L, "바인딩 검증", 1L);
    }

    @Test
    @DisplayName("크레딧 지급: memberId 42L, 금액, 사유, 토큰의 관리자 ID가 서비스에 올바르게 전달된다")
    void grantCredit_passesPathAndBodyCorrectly() throws Exception {
        String adminToken = createToken(1L, "admin@test.com", MemberRole.ADMIN);
        AdminMemberResponse response = createMemberResponse(42L, "u42@test.com", MemberStatus.ACTIVE);
        given(adminMemberService.grantCredit(eq(42L), eq(1000), eq("바인딩 검증"), eq(1L))).willReturn(response);

        mockMvc.perform(post("/api/v1/admin/members/42/credits")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000,\"reason\":\"바인딩 검증\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberId").value(42L));

        verify(adminMemberService).grantCredit(42L, 1000, "바인딩 검증", 1L);
    }
}
