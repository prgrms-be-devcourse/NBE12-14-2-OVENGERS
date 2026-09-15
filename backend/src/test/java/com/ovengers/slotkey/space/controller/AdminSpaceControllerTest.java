package com.ovengers.slotkey.space.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ovengers.slotkey.global.error.GlobalExceptionHandler;
import com.ovengers.slotkey.global.security.AuthPrincipal;
import com.ovengers.slotkey.member.entity.MemberRole;
import com.ovengers.slotkey.space.dto.request.SpaceCreateRequest;
import com.ovengers.slotkey.space.dto.response.SpaceDetailResponse;
import com.ovengers.slotkey.space.entity.SpaceStatus;
import com.ovengers.slotkey.space.service.AdminSpaceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalTime;

import static org.mockito.ArgumentMatchers.any;
import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.space.authorization.SpaceAuthorizationService;
import com.ovengers.slotkey.space.dto.request.SpaceUpdateRequest;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminSpaceControllerTest {

        private static final AuthPrincipal ADMIN_PRINCIPAL = new AuthPrincipal(1L, "admin@test.com", MemberRole.ADMIN);
        private static final AuthPrincipal USER_PRINCIPAL = new AuthPrincipal(2L, "user@test.com", MemberRole.USER);

        private MockMvc mockMvc;

        @Mock
        private AdminSpaceService adminSpaceService;

        @Mock
        private SpaceAuthorizationService spaceAuthorizationService;

        @InjectMocks
        private AdminSpaceController adminSpaceController;

        private ObjectMapper objectMapper;

        @BeforeEach
        void setUp() {
                objectMapper = new ObjectMapper();
                objectMapper.registerModule(new JavaTimeModule());

                mockMvc = MockMvcBuilders.standaloneSetup(adminSpaceController)
                                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                                .setControllerAdvice(new GlobalExceptionHandler())
                                .build();

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                ADMIN_PRINCIPAL, null, ADMIN_PRINCIPAL.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        @AfterEach
        void tearDown() {
                SecurityContextHolder.clearContext();
        }

        @Test
        @DisplayName("공간 등록 요청 시 201 Created와 ApiResponse 규격 응답을 반환한다")
        void createSpace_returnsCreated() throws Exception {
                // given
                SpaceCreateRequest request = new SpaceCreateRequest(
                                "회의실 1",
                                "서울시 강남구",
                                "깔끔한 회의실",
                                6,
                                5000L,
                                "/img.jpg",
                                LocalTime.of(9, 0),
                                LocalTime.of(18, 0));

                SpaceDetailResponse response = SpaceDetailResponse.builder()
                                .id(1L)
                                .name(request.name())
                                .location(request.location())
                                .description(request.description())
                                .capacity(request.capacity())
                                .pricePerSlot(request.pricePerSlot())
                                .imagePath(request.imagePath())
                                .openingTime(request.openingTime())
                                .closingTime(request.closingTime())
                                .status(SpaceStatus.ACTIVE)
                                .build();

                given(adminSpaceService.createSpace(any(SpaceCreateRequest.class), eq(1L))).willReturn(response);

                // when & then
                mockMvc.perform(post("/api/v1/admin/spaces")
                                .header("X-Actor-Member-Id", 1L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.status").value("SUCCESS"))
                                .andExpect(jsonPath("$.code").value("OK"))
                                .andExpect(jsonPath("$.data.id").value(1L))
                                .andExpect(jsonPath("$.data.name").value("회의실 1"));

                verify(spaceAuthorizationService).validateCanManageSpace(ADMIN_PRINCIPAL);
                verify(adminSpaceService).createSpace(any(SpaceCreateRequest.class), eq(ADMIN_PRINCIPAL.memberId()));
        }

        @Test
        @DisplayName("필수 필드(이름 누락 등) 유효성 검증 실패 시 400 Bad Request를 반환한다")
        void createSpace_validationFailed() throws Exception {
                // name이 빈 문자열인 요청
                SpaceCreateRequest invalidRequest = new SpaceCreateRequest(
                                "",
                                "서울시 강남구",
                                "설명",
                                6,
                                5000L,
                                null,
                                LocalTime.of(9, 0),
                                LocalTime.of(18, 0));

                mockMvc.perform(post("/api/v1/admin/spaces")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(invalidRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.status").value("FAIL"))
                                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

                verifyNoInteractions(adminSpaceService);
        }

        @Test
        @DisplayName("공간 수정 요청 시 200 OK와 수정된 공간 상세를 반환한다")
        void updateSpace_returnsOk() throws Exception {
                // given
                Long spaceId = 1L;
                SpaceUpdateRequest request = new SpaceUpdateRequest(
                                null,
                                "수정된 회의실",
                                "서울시 서초구",
                                "업그레이드된 회의실",
                                8,
                                6000L,
                                "/new-img.jpg",
                                LocalTime.of(10, 0),
                                LocalTime.of(20, 0),
                                SpaceStatus.ACTIVE);

                SpaceDetailResponse response = SpaceDetailResponse.builder()
                                .id(spaceId)
                                .name(request.name())
                                .location(request.location())
                                .description(request.description())
                                .capacity(request.capacity())
                                .pricePerSlot(request.pricePerSlot())
                                .imagePath(request.imagePath())
                                .openingTime(request.openingTime())
                                .closingTime(request.closingTime())
                                .status(request.status())
                                .version(1)
                                .build();

                given(adminSpaceService.updateSpace(eq(spaceId), any(SpaceUpdateRequest.class),
                                eq(ADMIN_PRINCIPAL.memberId())))
                                .willReturn(response);

                // when & then
                mockMvc.perform(patch("/api/v1/admin/spaces/{spaceId}", spaceId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("SUCCESS"))
                                .andExpect(jsonPath("$.code").value("OK"))
                                .andExpect(jsonPath("$.data.id").value(spaceId))
                                .andExpect(jsonPath("$.data.name").value("수정된 회의실"))
                                .andExpect(jsonPath("$.data.pricePerSlot").value(6000L))
                                .andExpect(jsonPath("$.data.version").value(1));

                verify(spaceAuthorizationService).validateCanManageSpace(ADMIN_PRINCIPAL);
                verify(adminSpaceService).updateSpace(eq(spaceId), any(SpaceUpdateRequest.class),
                                eq(ADMIN_PRINCIPAL.memberId()));
        }

        @Test
        @DisplayName("존재하지 않는 공간 수정 시 404 Not Found와 SPACE_NOT_FOUND 에러 코드를 반환한다")
        void updateSpace_notFound() throws Exception {
                // given
                Long notFoundSpaceId = 999L;
                SpaceUpdateRequest request = new SpaceUpdateRequest(
                                null,
                                "수정 회의실",
                                "서울",
                                null,
                                4,
                                5000L,
                                null,
                                null,
                                null,
                                null);

                given(adminSpaceService.updateSpace(eq(notFoundSpaceId), any(SpaceUpdateRequest.class),
                                eq(ADMIN_PRINCIPAL.memberId())))
                                .willThrow(new BusinessException(ErrorCode.SPACE_NOT_FOUND));

                // when & then
                mockMvc.perform(patch("/api/v1/admin/spaces/{spaceId}", notFoundSpaceId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.status").value("FAIL"))
                                .andExpect(jsonPath("$.code").value("SPACE_NOT_FOUND"));
        }

        @Test
        @DisplayName("관리자 권한이 없는 사용자(USER)가 공간 등록 시 403 Forbidden을 반환한다")
        void createSpace_accessDenied_forUser() throws Exception {
                // given
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                USER_PRINCIPAL, null, USER_PRINCIPAL.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);

                willThrow(new BusinessException(ErrorCode.ACCESS_DENIED))
                                .given(spaceAuthorizationService).validateCanManageSpace(USER_PRINCIPAL);

                SpaceCreateRequest request = new SpaceCreateRequest(
                                "회의실 1",
                                "서울시 강남구",
                                "깔끔한 회의실",
                                6,
                                5000L,
                                null,
                                LocalTime.of(9, 0),
                                LocalTime.of(18, 0));

                // when & then
                mockMvc.perform(post("/api/v1/admin/spaces")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isForbidden())
                                .andExpect(jsonPath("$.status").value("FAIL"))
                                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

                verifyNoInteractions(adminSpaceService);
        }

        @Test
        @DisplayName("인증 정보가 없는(null) 사용자가 공간 등록 시 403 Forbidden을 반환한다")
        void createSpace_accessDenied_forNullPrincipal() throws Exception {
                // given
                SecurityContextHolder.clearContext();

                willThrow(new BusinessException(ErrorCode.ACCESS_DENIED))
                                .given(spaceAuthorizationService).validateCanManageSpace(null);

                SpaceCreateRequest request = new SpaceCreateRequest(
                                "회의실 1",
                                "서울시 강남구",
                                "깔끔한 회의실",
                                6,
                                5000L,
                                null,
                                LocalTime.of(9, 0),
                                LocalTime.of(18, 0));

                // when & then
                mockMvc.perform(post("/api/v1/admin/spaces")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isForbidden())
                                .andExpect(jsonPath("$.status").value("FAIL"))
                                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

                verifyNoInteractions(adminSpaceService);
        }
}
