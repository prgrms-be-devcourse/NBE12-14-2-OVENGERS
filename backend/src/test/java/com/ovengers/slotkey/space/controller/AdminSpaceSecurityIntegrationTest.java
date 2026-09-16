package com.ovengers.slotkey.space.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ovengers.slotkey.global.security.jwt.JwtUtil;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.entity.MemberRole;
import com.ovengers.slotkey.member.entity.MemberStatus;
import com.ovengers.slotkey.member.repository.MemberRepository;
import com.ovengers.slotkey.space.authorization.SpaceAuthorizationService;
import com.ovengers.slotkey.space.dto.request.SpaceCreateRequest;
import com.ovengers.slotkey.space.dto.response.SpaceDetailResponse;
import com.ovengers.slotkey.space.entity.SpaceStatus;
import com.ovengers.slotkey.space.service.AdminSpaceService;
import com.ovengers.slotkey.space.service.SpaceQueryService;
import com.ovengers.slotkey.space.service.SpaceSlotAvailabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.LocalTime;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실제 SecurityFilterChain을 통과시키는 보안 통합 테스트.
 * CustomAuthenticationFilter → SecurityConfig의 역할 기반 인가 → AdminSpaceController
 * 순서로
 * 전체 보안 파이프라인을 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminSpaceSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminSpaceService adminSpaceService;

    @MockBean
    private SpaceAuthorizationService spaceAuthorizationService;

    @MockBean
    private MemberRepository memberRepository;

    // JPA 관련 빈들을 Mock으로 대체
    @MockBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @MockBean
    private SpaceQueryService spaceQueryService;

    @MockBean
    private SpaceSlotAvailabilityService spaceSlotAvailabilityService;

    @MockBean
    private Clock clock;

    @Value("${custom.jwt.secret-key}")
    private String secretKey;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private Member adminMember;
    private Member userMember;
    private Member suspendedMember;

    @BeforeEach
    void setUp() {
        adminMember = new Member("admin@test.com", "hash", "관리자");
        ReflectionTestUtils.setField(adminMember, "id", 1L);
        ReflectionTestUtils.setField(adminMember, "role", MemberRole.ADMIN);
        ReflectionTestUtils.setField(adminMember, "status", MemberStatus.ACTIVE);

        userMember = new Member("user@test.com", "hash", "유저");
        ReflectionTestUtils.setField(userMember, "id", 2L);
        ReflectionTestUtils.setField(userMember, "role", MemberRole.USER);
        ReflectionTestUtils.setField(userMember, "status", MemberStatus.ACTIVE);

        suspendedMember = new Member("suspended@test.com", "hash", "정지유저");
        ReflectionTestUtils.setField(suspendedMember, "id", 3L);
        ReflectionTestUtils.setField(suspendedMember, "role", MemberRole.ADMIN);
        ReflectionTestUtils.setField(suspendedMember, "status", MemberStatus.SUSPENDED);
    }

    private String generateToken(long memberId, String email) {
        return JwtUtil.createToken(secretKey, 900_000L, Map.of("id", memberId, "email", email));
    }

    private SpaceCreateRequest validRequest() {
        return new SpaceCreateRequest(
                "회의실 1", "서울시 강남구", "깔끔한 회의실",
                6, 5000L, "/img.jpg",
                LocalTime.of(9, 0), LocalTime.of(18, 0));
    }

    @Test
    @DisplayName("비인증 요청 POST /api/v1/admin/spaces → 401 AUTHENTICATION_REQUIRED")
    void noToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/admin/spaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("USER 토큰 요청 POST /api/v1/admin/spaces → 403 ACCESS_DENIED")
    void userToken_returns403() throws Exception {
        given(memberRepository.findById(2L)).willReturn(Optional.of(userMember));
        String token = generateToken(2L, "user@test.com");

        mockMvc.perform(post("/api/v1/admin/spaces")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("ADMIN 토큰 요청 POST /api/v1/admin/spaces → 201 Created")
    void adminToken_returns201() throws Exception {
        given(memberRepository.findById(1L)).willReturn(Optional.of(adminMember));
        String token = generateToken(1L, "admin@test.com");

        SpaceDetailResponse response = SpaceDetailResponse.builder()
                .id(10L)
                .name("회의실 1")
                .location("서울시 강남구")
                .description("깔끔한 회의실")
                .capacity(6)
                .pricePerSlot(5000L)
                .imagePath("/img.jpg")
                .openingTime(LocalTime.of(9, 0))
                .closingTime(LocalTime.of(18, 0))
                .status(SpaceStatus.ACTIVE)
                .build();

        given(adminSpaceService.createSpace(any(SpaceCreateRequest.class), eq(1L))).willReturn(response);

        mockMvc.perform(post("/api/v1/admin/spaces")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").value(10L));
    }

    @Test
    @DisplayName("정지된 회원의 유효한 토큰 요청 → 403 ACCOUNT_INACTIVE")
    void suspendedMemberToken_returns403() throws Exception {
        given(memberRepository.findById(3L)).willReturn(Optional.of(suspendedMember));
        String token = generateToken(3L, "suspended@test.com");

        mockMvc.perform(post("/api/v1/admin/spaces")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_INACTIVE"));
    }
}
