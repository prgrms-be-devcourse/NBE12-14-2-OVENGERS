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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminSpaceControllerTest {

        private static final AuthPrincipal ADMIN_PRINCIPAL = new AuthPrincipal(1L, "admin@test.com", MemberRole.ADMIN);

        private MockMvc mockMvc;

        @Mock
        private AdminSpaceService adminSpaceService;

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
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.status").value("SUCCESS"))
                                .andExpect(jsonPath("$.code").value("OK"))
                                .andExpect(jsonPath("$.data.id").value(1L))
                                .andExpect(jsonPath("$.data.name").value("회의실 1"));

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
        }
}
