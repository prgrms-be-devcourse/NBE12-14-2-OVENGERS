package com.ovengers.slotkey.reservation.controller;

import com.ovengers.slotkey.global.security.jwt.JwtProvider;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.entity.MemberRole;
import com.ovengers.slotkey.member.repository.MemberRepository;
import com.ovengers.slotkey.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class AdminReservationSecurityIntegrationTest extends IntegrationTestSupport {

    private static final String ADMIN_RESERVATIONS_PATH = "/api/v1/admin/reservations";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JwtProvider jwtProvider;

    @Test
    @DisplayName("미인증 사용자가 관리자 예약 목록을 요청하면 401 Unauthorized와 AUTHENTICATION_REQUIRED를 반환한다")
    void getReservations_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(ADMIN_RESERVATIONS_PATH)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("FAIL"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("일반 회원(USER)이 관리자 예약 목록을 요청하면 403 Forbidden과 ACCESS_DENIED를 반환한다")
    void getReservations_userRole_returns403() throws Exception {
        Member user = memberRepository.save(new Member("user@slotkey.test", "{noop}pass1234", "일반회원"));
        String token = jwtProvider.genAccessToken(user);

        mockMvc.perform(get(ADMIN_RESERVATIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("FAIL"))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("관리자(ADMIN)가 관리자 예약 목록을 요청하면 200 OK와 PageResponse를 반환한다")
    void getReservations_adminRole_returns200() throws Exception {
        Member admin = new Member("admin@slotkey.test", "{noop}admin1234", "관리자");
        ReflectionTestUtils.setField(admin, "role", MemberRole.ADMIN);
        admin = memberRepository.save(admin);
        String token = jwtProvider.genAccessToken(admin);

        mockMvc.perform(get(ADMIN_RESERVATIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20));
    }
}
