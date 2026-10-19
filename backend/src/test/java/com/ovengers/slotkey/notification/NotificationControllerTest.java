package com.ovengers.slotkey.notification;

import com.ovengers.slotkey.global.config.SecurityConfig;
import com.ovengers.slotkey.global.config.ClockConfig;
import com.ovengers.slotkey.auth.service.AuthSessionService;
import com.ovengers.slotkey.global.config.WebMvcConfig;
import com.ovengers.slotkey.global.error.GlobalExceptionHandler;
import com.ovengers.slotkey.global.security.CurrentMemberArgumentResolver;
import com.ovengers.slotkey.global.security.jwt.JwtProvider;
import com.ovengers.slotkey.global.security.jwt.JwtUtil;
import com.ovengers.slotkey.member.entity.MemberRole;
import com.ovengers.slotkey.notification.controller.NotificationController;
import com.ovengers.slotkey.notification.dto.response.NotificationResponse;
import com.ovengers.slotkey.notification.service.NotificationQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 알림 조회 API 웹 계층: 토큰의 회원 id로만 조회하는지(본인 것만 보이는 출발점)와 인증 강제를 검증한다. */
@WebMvcTest(NotificationController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, ClockConfig.class, JwtProvider.class, WebMvcConfig.class, CurrentMemberArgumentResolver.class,
        GlobalExceptionHandler.class})
class NotificationControllerTest {

    private static final long MEMBER_ID = 7L;

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private NotificationQueryService service;
    @MockBean
    private AuthSessionService authSessionService;

    @BeforeEach
    void allowTestSession() {
        org.mockito.BDDMockito.given(authSessionService.validate(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong()))
                .willReturn(AuthSessionService.ValidationResult.VALID);
    }
    @Value("${custom.jwt.secret-key}")
    private String secretKey;

    private String userToken() {
        return JwtUtil.createToken(secretKey, 300_000L,
                Map.of("id", MEMBER_ID, "email", "user@slotkey.test", "role", MemberRole.USER.name(), "sid", "test-session"));
    }

    @Test
    @DisplayName("목록: 토큰의 회원 id로 조회하고 페이지 응답을 돌려준다")
    void list_usesTokenMemberId() throws Exception {
        NotificationResponse n = new NotificationResponse(1L, 11L, "자리가 비었습니다", false,
                LocalDateTime.of(2026, 10, 16, 12, 0));
        given(service.getMyNotifications(any(), any()))
                .willReturn(new PageImpl<>(List.of(n), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer " + userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(1))
                .andExpect(jsonPath("$.data.content[0].read").value(false))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        verify(service).getMyNotifications(org.mockito.ArgumentMatchers.eq(MEMBER_ID), any());
    }

    @Test
    @DisplayName("안 읽은 개수: 토큰의 회원 id로 센다")
    void unreadCount_usesTokenMemberId() throws Exception {
        given(service.countUnread(MEMBER_ID)).willReturn(3L);

        mockMvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", "Bearer " + userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(3));
    }

    @Test
    @DisplayName("토큰이 없으면 두 API 모두 401이고 서비스를 호출하지 않는다")
    void noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/notifications/unread-count")).andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }
}
