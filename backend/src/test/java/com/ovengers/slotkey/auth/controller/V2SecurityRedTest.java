package com.ovengers.slotkey.auth.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ovengers.slotkey.auth.entity.LocalCredential;
import com.ovengers.slotkey.auth.entity.VerificationPurpose;
import com.ovengers.slotkey.auth.mail.VerificationMailSender;
import com.ovengers.slotkey.auth.service.VerificationChallengeService;
import com.ovengers.slotkey.auth.service.VerificationCheckService;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.entity.MemberStatus;
import com.ovengers.slotkey.member.repository.MemberRepository;
import com.ovengers.slotkey.auth.repository.LocalCredentialRepository;
import com.ovengers.slotkey.support.MySqlTestContainerConfig;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V2 보안 기준 레드 테스트.
 *
 * 현재 V1 구현에서는 의도적으로 실패한다.
 * V2의 세션 조회, 토큰 폐기, rotation, 요청 제한 구현 후 통과해야 한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(MySqlTestContainerConfig.class)
@Transactional
class V2SecurityRedTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private LocalCredentialRepository localCredentialRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private VerificationChallengeService challengeService;

    @Autowired
    private VerificationCheckService checkService;

    @MockBean
    private VerificationMailSender mailSender;

    @Test
    @DisplayName("V2: 로그아웃하면 기존 Access Token은 즉시 폐기된다")
    void logout_invalidates_existing_access_token() throws Exception {
        String email = newEmail("logout");
        MvcResult login = login(email);
        String accessToken = accessToken(login);
        Cookie refreshCookie = login.getResponse().getCookie("refreshToken");

        mvc.perform(post("/api/v1/auth/logout").cookie(refreshCookie))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/members/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("V2: 비밀번호 재설정이 성공하면 기존 Access Token은 즉시 폐기된다")
    void password_reset_invalidates_existing_access_token() throws Exception {
        String email = newEmail("password-reset");
        MvcResult login = login(email);
        String accessToken = accessToken(login);
        String grant = issueGrant(email, VerificationPurpose.PASSWORD_RESET);

        mvc.perform(post("/api/v1/auth/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "grant", grant,
                                "password", "New1234!",
                                "passwordConfirm", "New1234!"
                        ))))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/members/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("V2: Refresh Token은 재사용할 수 없다")
    void refresh_token_is_rotated_and_cannot_be_reused() throws Exception {
        String email = newEmail("rotation");
        MvcResult login = login(email);
        Cookie originalRefreshCookie = login.getResponse().getCookie("refreshToken");

        mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(originalRefreshCookie))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(originalRefreshCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("V2: 회원이 정지되면 기존 Access Token도 즉시 폐기된다")
    void suspended_member_cannot_use_existing_access_token() throws Exception {
        String email = newEmail("suspended");
        MvcResult login = login(email);
        String accessToken = accessToken(login);

        Member member = memberRepository.findByEmail(email).orElseThrow();
        member.updateStatus(MemberStatus.SUSPENDED);
        memberRepository.saveAndFlush(member);

        mvc.perform(get("/api/v1/members/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("V2: 반복 로그인 실패는 요청 제한 응답을 반환한다")
    void repeated_login_failures_are_rate_limited() throws Exception {
        String email = newEmail("rate-limit");
        login(email);

        for (int attempt = 1; attempt <= 5; attempt++) {
            mvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "email", email,
                                    "password", "Wrong1234!"
                            ))))
                    .andExpect(status().isUnauthorized());
        }

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "Wrong1234!"
                        ))))
                .andExpect(status().isTooManyRequests());
    }

    private MvcResult login(String email) throws Exception {
        String password = "Test1234!";
        Member member = memberRepository.saveAndFlush(new Member(email, "테스트회원"));
        localCredentialRepository.saveAndFlush(new LocalCredential(
                member,
                passwordEncoder.encode(password),
                LocalDateTime.now()
        ));

        return mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andReturn();
    }

    private String accessToken(MvcResult result) throws Exception {
        JsonNode data = objectMapper.readTree(
                result.getResponse().getContentAsString()
        ).path("data");
        String token = data.path("accessToken").asText();
        assertThat(token).isNotBlank();
        return token;
    }

    private String issueGrant(String email, VerificationPurpose purpose) {
        AtomicReference<String> deliveredCode = new AtomicReference<>();

        doAnswer(invocation -> {
            deliveredCode.set(invocation.getArgument(2, String.class));
            return null;
        }).when(mailSender).sendCode(eq(email), eq(purpose), anyString());

        var issued = challengeService.issue(email, purpose);
        return checkService.verify(
                email, purpose, issued.challengeId(), deliveredCode.get()
        ).grant();
    }

    private String newEmail(String prefix) {
        return "v2-" + prefix + "-" + UUID.randomUUID() + "@example.com";
    }
}
