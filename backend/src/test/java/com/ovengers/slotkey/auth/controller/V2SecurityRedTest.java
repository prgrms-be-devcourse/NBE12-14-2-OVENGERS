package com.ovengers.slotkey.auth.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ovengers.slotkey.auth.entity.LocalCredential;
import com.ovengers.slotkey.auth.entity.RefreshToken;
import com.ovengers.slotkey.auth.repository.RefreshTokenRepository;
import com.ovengers.slotkey.auth.entity.VerificationPurpose;
import com.ovengers.slotkey.auth.mail.VerificationMailSender;
import com.ovengers.slotkey.auth.service.VerificationChallengeService;
import com.ovengers.slotkey.auth.service.VerificationCheckService;
import com.ovengers.slotkey.auth.service.AuthService;
import com.ovengers.slotkey.auth.service.PasswordResetService;
import com.ovengers.slotkey.global.security.jwt.JwtProvider;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.entity.MemberRole;
import com.ovengers.slotkey.member.entity.MemberStatus;
import com.ovengers.slotkey.member.repository.MemberRepository;
import com.ovengers.slotkey.auth.repository.LocalCredentialRepository;
import com.ovengers.slotkey.support.MySqlTestContainerConfig;
import com.ovengers.slotkey.support.SettableClockConfig;
import com.ovengers.slotkey.support.SettableClockConfig.SettableClock;
import jakarta.servlet.http.Cookie;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V2 보안 회귀 테스트.
 *
 * 완료된 V2 범위와 RT Rotation 목표는 활성 상태로 검증하고,
 * 로그인 요청 제한 테스트만 4번 이슈 구현 전까지 비활성 상태로 유지한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import({MySqlTestContainerConfig.class, SettableClockConfig.class})
class V2SecurityRedTest {

    private static final String PASSWORD = "Test1234!";
    private static final String NEW_PASSWORD = "New1234!";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @SpyBean
    private MemberRepository memberRepository;

    @Autowired
    private LocalCredentialRepository localCredentialRepository;

    @SpyBean
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private VerificationChallengeService challengeService;

    @Autowired
    private VerificationCheckService checkService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    @Autowired
    private SettableClock settableClock;

    @MockBean
    private VerificationMailSender mailSender;

    @BeforeEach
    void setTestTime() {
        settableClock.set(ZonedDateTime.of(
                2027, 3, 1, 0, 1, 0, 0, SettableClockConfig.KST
        ));
    }

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
        String oldAccessToken = accessToken(login);
        Cookie originalRefreshCookie = login.getResponse().getCookie("refreshToken");
        MvcResult siblingLogin = loginExisting(email, PASSWORD);
        String siblingAccessToken = accessToken(siblingLogin);
        Cookie siblingRefreshCookie = refreshCookie(siblingLogin);

        MvcResult rotation = mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(originalRefreshCookie))
                .andExpect(status().isOk())
                .andReturn();
        Cookie rotatedCookie = refreshCookie(rotation);

        mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(originalRefreshCookie))
                .andExpect(status().isUnauthorized());

        assertThat(refreshToken(originalRefreshCookie.getValue()).getConsumedAt())
                .isNotNull();
        assertThat(refreshToken(originalRefreshCookie.getValue()).getRevokedAt())
                .isNotNull();
        assertThat(refreshToken(rotatedCookie.getValue()).getRevokedAt())
                .isNotNull();
        assertThatSessionRevoked(originalRefreshCookie.getValue());

        mvc.perform(get("/api/v1/members/me")
                        .header("Authorization", "Bearer " + oldAccessToken))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").cookie(rotatedCookie))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/members/me")
                        .header("Authorization", "Bearer " + siblingAccessToken))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/refresh").cookie(siblingRefreshCookie))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("V2: 관리자가 회원을 정지하면 기존 AT와 RT 세션이 영구 폐기된다")
    void suspended_member_cannot_use_existing_access_token() throws Exception {
        String email = newEmail("suspended");
        MvcResult firstLogin = login(email);
        MvcResult secondLogin = loginExisting(email, PASSWORD);
        String firstAccessToken = accessToken(firstLogin);
        String secondAccessToken = accessToken(secondLogin);
        Cookie firstRefreshToken = refreshCookie(firstLogin);
        Cookie secondRefreshToken = refreshCookie(secondLogin);

        Member member = memberRepository.findByEmail(email).orElseThrow();
        Member admin = createAdminWithCredential();
        String adminAccessToken = accessToken(loginExisting(admin.getEmail(), PASSWORD));

        mvc.perform(patch("/api/v1/admin/members/{memberId}/suspend", member.getId())
                        .header("Authorization", "Bearer " + adminAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "reason", "V2 세션 폐기 검증"
                        ))))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/members/me")
                        .header("Authorization", "Bearer " + firstAccessToken))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/members/me")
                        .header("Authorization", "Bearer " + secondAccessToken))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/auth/refresh").cookie(firstRefreshToken))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").cookie(secondRefreshToken))
                .andExpect(status().isUnauthorized());
    }

    @Disabled("V2 구현 전까지 CI에서 비활성화")
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

    @Test
    @DisplayName("V2: 정상 사용자는 RT Rotation 후 새 RT로 계속 갱신할 수 있다")
    void refresh_token_rotation_allows_next_refresh_with_new_token() throws Exception {
        String email = newEmail("rotation-success");
        MvcResult login = login(email);
        Cookie rt1 = refreshCookie(login);
        LocalDateTime sessionExpiry = refreshToken(rt1.getValue()).getExpiresAt();
        assertThat(cookieMaxAge(login)).isEqualTo(24 * 60 * 60L);

        settableClock.set(ZonedDateTime.of(
                2027, 3, 1, 4, 1, 0, 0, SettableClockConfig.KST
        ));

        MvcResult firstRefresh = mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(rt1))
                .andExpect(status().isOk())
                .andReturn();

        assertAccessTokenLifetime(accessToken(firstRefresh), sessionExpiry);
        Cookie rt2 = refreshCookie(firstRefresh);
        assertThat(rt2).as("첫 refresh 응답은 RT2를 발급해야 한다")
                .isNotNull();
        assertThat(rt2.getValue()).isNotBlank().isNotEqualTo(rt1.getValue());
        assertThat(cookieMaxAge(firstRefresh)).isEqualTo(20 * 60 * 60L);
        assertThat(refreshToken(rt1.getValue()).getConsumedAt())
                .isEqualTo(LocalDateTime.of(2027, 3, 1, 4, 1));
        assertThat(refreshToken(rt2.getValue()).getExpiresAt()).isEqualTo(sessionExpiry);

        settableClock.set(ZonedDateTime.of(
                2027, 3, 1, 7, 1, 0, 0, SettableClockConfig.KST
        ));

        MvcResult secondRefresh = mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(rt2))
                .andExpect(status().isOk())
                .andReturn();

        Cookie rt3 = refreshCookie(secondRefresh);
        assertThat(rt3).as("두 번째 refresh 응답은 RT3를 발급해야 한다")
                .isNotNull();
        assertThat(rt3.getValue()).isNotBlank().isNotEqualTo(rt2.getValue());
        assertAccessTokenLifetime(accessToken(secondRefresh), sessionExpiry);
        assertThat(secondRefresh.getResponse().getHeader("Set-Cookie"))
                .contains("HttpOnly", "Max-Age=");
        assertThat(cookieMaxAge(secondRefresh)).isEqualTo(17 * 60 * 60L);
        assertThat(refreshToken(rt2.getValue()).getConsumedAt())
                .isEqualTo(LocalDateTime.of(2027, 3, 1, 7, 1));
        assertThat(refreshToken(rt3.getValue()).getExpiresAt()).isEqualTo(sessionExpiry);

        settableClock.set(ZonedDateTime.of(
                2027, 3, 2, 0, 1, 0, 0, SettableClockConfig.KST
        ));
        mvc.perform(post("/api/v1/auth/refresh").cookie(rt3))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("V2: 비밀번호 재설정 후 기존 Refresh Token은 폐기된다")
    void password_reset_invalidates_existing_refresh_token() throws Exception {
        String email = newEmail("password-reset-refresh");
        MvcResult login = login(email);
        Cookie oldRefreshToken = refreshCookie(login);
        String grant = issueGrant(email, VerificationPurpose.PASSWORD_RESET);

        resetPassword(email, grant, NEW_PASSWORD);

        mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(oldRefreshToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("V2: 비밀번호 재설정은 여러 로그인 세션의 AT와 RT를 모두 폐기한다")
    void password_reset_invalidates_all_existing_sessions() throws Exception {
        String email = newEmail("password-reset-sessions");
        String otherEmail = newEmail("unaffected-member");
        createMemberWithCredential(email, PASSWORD);
        MvcResult otherMemberLogin = login(otherEmail);
        String otherMemberAccessToken = accessToken(otherMemberLogin);
        Cookie otherMemberRefreshToken = refreshCookie(otherMemberLogin);

        MvcResult firstLogin = loginExisting(email, PASSWORD);
        MvcResult secondLogin = loginExisting(email, PASSWORD);
        String at1 = accessToken(firstLogin);
        String at2 = accessToken(secondLogin);
        Cookie rt1 = refreshCookie(firstLogin);
        Cookie rt2 = refreshCookie(secondLogin);
        assertThat(rt1.getValue()).isNotEqualTo(rt2.getValue());

        String grant = issueGrant(email, VerificationPurpose.PASSWORD_RESET);
        resetPassword(email, grant, NEW_PASSWORD);

        int at1Status = mvc.perform(get("/api/v1/members/me")
                        .header("Authorization", "Bearer " + at1))
                .andReturn().getResponse().getStatus();
        int at2Status = mvc.perform(get("/api/v1/members/me")
                        .header("Authorization", "Bearer " + at2))
                .andReturn().getResponse().getStatus();
        int rt1Status = mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(rt1))
                .andReturn().getResponse().getStatus();
        int rt2Status = mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(rt2))
                .andReturn().getResponse().getStatus();

        assertThat(List.of(at1Status, at2Status, rt1Status, rt2Status))
                .as("비밀번호 재설정 후 모든 기존 세션은 401이어야 한다")
                .containsOnly(401);

        MvcResult freshLogin = loginExisting(email, NEW_PASSWORD);
        mvc.perform(get("/api/v1/members/me")
                        .header("Authorization", "Bearer " + accessToken(freshLogin)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie(freshLogin)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/members/me")
                        .header("Authorization", "Bearer " + otherMemberAccessToken))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/refresh").cookie(otherMemberRefreshToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("V2: 회원 정지 후 복구해도 정지 전 AT와 RT는 다시 살아나지 않는다")
    void member_restore_does_not_revive_old_session() throws Exception {
        String email = newEmail("restore-session");
        MvcResult login = login(email);
        String oldAccessToken = accessToken(login);
        Cookie oldRefreshToken = refreshCookie(login);
        Member member = memberRepository.findByEmail(email).orElseThrow();
        Member admin = createAdminWithCredential();
        String adminAccessToken = accessToken(
                loginExisting(admin.getEmail(), PASSWORD)
        );

        mvc.perform(patch("/api/v1/admin/members/{memberId}/suspend", member.getId())
                        .header("Authorization", "Bearer " + adminAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "reason", "V2 레드 테스트 정지"
                        ))))
                .andExpect(status().isOk());

        mvc.perform(patch("/api/v1/admin/members/{memberId}/restore", member.getId())
                        .header("Authorization", "Bearer " + adminAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "reason", "V2 레드 테스트 복구"
                        ))))
                .andExpect(status().isOk());

        int oldAccessStatus = mvc.perform(get("/api/v1/members/me")
                        .header("Authorization", "Bearer " + oldAccessToken))
                .andReturn().getResponse().getStatus();
        int oldRefreshStatus = mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(oldRefreshToken))
                .andReturn().getResponse().getStatus();

        assertThat(List.of(oldAccessStatus, oldRefreshStatus))
                .as("회원 정지·복구 후 정지 전 세션은 모두 401이어야 한다")
                .containsOnly(401);

        MvcResult freshLogin = loginExisting(email, PASSWORD);
        mvc.perform(get("/api/v1/members/me")
                        .header("Authorization", "Bearer " + accessToken(freshLogin)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie(freshLogin)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("V2: 회원탈퇴 후 기존 모든 로그인 세션의 AT와 RT가 거부된다")
    void withdrawal_invalidates_existing_access_token() throws Exception {
        String email = newEmail("withdrawal");
        MvcResult firstLogin = login(email);
        MvcResult secondLogin = loginExisting(email, PASSWORD);
        String firstAccessToken = accessToken(firstLogin);
        String secondAccessToken = accessToken(secondLogin);
        Cookie firstRefreshToken = refreshCookie(firstLogin);
        Cookie secondRefreshToken = refreshCookie(secondLogin);

        mvc.perform(post("/api/v1/auth/withdraw")
                        .header("Authorization", "Bearer " + firstAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", PASSWORD
                        ))))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/members/me")
                        .header("Authorization", "Bearer " + firstAccessToken))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/members/me")
                        .header("Authorization", "Bearer " + secondAccessToken))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/auth/refresh").cookie(firstRefreshToken))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").cookie(secondRefreshToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("V2: 인증 최종 잠금의 오류 응답 후 정지·세션 폐기는 커밋되고 복구 후에도 옛 토큰은 거부된다")
    void final_verification_lock_commits_and_permanently_revokes_sessions() throws Exception {
        String email = newEmail("verification-final-lock");
        MvcResult userLogin = login(email);
        String oldAccessToken = accessToken(userLogin);
        Cookie oldRefreshToken = refreshCookie(userLogin);
        Member member = memberRepository.findByEmail(email).orElseThrow();

        Member admin = createAdminWithCredential();
        String adminAccessToken = accessToken(loginExisting(admin.getEmail(), PASSWORD));
        VerificationPurpose purpose = VerificationPurpose.PASSWORD_RESET;

        for (int stage = 1; stage <= 3; stage++) {
            IssuedChallenge issued = issueChallengeFromApi(email, purpose);
            String wrongCode = issued.deliveredCode().equals("00000000")
                    ? "00000001"
                    : "00000000";

            for (int attempt = 1; attempt <= 5; attempt++) {
                var response = mvc.perform(post("/api/v1/auth/verification/check")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "email", email,
                                        "purpose", purpose.name(),
                                        "challengeId", issued.challengeId(),
                                        "code", wrongCode
                                ))))
                        .andReturn().getResponse();

                if (attempt < 5) {
                    assertThat(response.getStatus()).isEqualTo(400);
                    assertThat(objectMapper.readTree(response.getContentAsString())
                            .path("code").asText())
                            .isEqualTo("VERIFICATION_CODE_MISMATCH");
                } else if (stage < 3) {
                    assertThat(response.getStatus()).isEqualTo(429);
                    assertThat(objectMapper.readTree(response.getContentAsString())
                            .path("code").asText())
                            .isEqualTo("VERIFICATION_TEMPORARILY_BLOCKED");
                } else {
                    assertThat(response.getStatus()).isEqualTo(403);
                    assertThat(objectMapper.readTree(response.getContentAsString())
                            .path("code").asText())
                            .isEqualTo("VERIFICATION_ADMIN_LOCKED");
                }
            }

            if (stage < 3) {
                releaseVerificationBlock(email, purpose);
            }
        }

        assertThat(memberRepository.findById(member.getId()).orElseThrow().getStatus())
                .isEqualTo(MemberStatus.SUSPENDED);

        mvc.perform(patch("/api/v1/admin/members/{memberId}/restore", member.getId())
                        .header("Authorization", "Bearer " + adminAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "reason", "V2 인증 잠금 복구 검증"
                        ))))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/members/me")
                        .header("Authorization", "Bearer " + oldAccessToken))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").cookie(oldRefreshToken))
                .andExpect(status().isUnauthorized());

        MvcResult freshLogin = loginExisting(email, PASSWORD);
        mvc.perform(get("/api/v1/members/me")
                        .header("Authorization", "Bearer " + accessToken(freshLogin)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("V2: 비밀번호 재설정과 이미 시작된 refresh가 경합해도 옛 세션은 재사용할 수 없다")
    void password_reset_racing_refresh_cannot_leave_old_session_usable() throws Exception {
        String email = newEmail("reset-refresh-race");
        MvcResult login = login(email);
        String oldAccessToken = accessToken(login);
        Cookie oldRefreshToken = refreshCookie(login);
        String grant = issueGrant(email, VerificationPurpose.PASSWORD_RESET);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<ConcurrentResponse> resetFuture = null;
        Future<ConcurrentResponse> refreshFuture = null;
        try {
            resetFuture = executor.submit(() -> concurrentRequest(
                    ready,
                    start,
                    () -> mvc.perform(post("/api/v1/auth/password-reset")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(Map.of(
                                            "email", email,
                                            "grant", grant,
                                            "password", NEW_PASSWORD,
                                            "passwordConfirm", NEW_PASSWORD
                                    ))))
                            .andReturn()
            ));
            refreshFuture = executor.submit(() -> concurrentRequest(
                    ready,
                    start,
                    () -> mvc.perform(post("/api/v1/auth/refresh")
                                    .cookie(oldRefreshToken))
                            .andReturn()
            ));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            ConcurrentResponse resetResult = resetFuture.get(10, TimeUnit.SECONDS);
            ConcurrentResponse refreshResult = refreshFuture.get(10, TimeUnit.SECONDS);
            assertThat(resetResult.failure()).isNull();
            assertThat(refreshResult.failure()).isNull();
            assertThat(resetResult.status()).isEqualTo(200);
            assertThat(refreshResult.status()).isIn(200, 401);

            mvc.perform(get("/api/v1/members/me")
                            .header("Authorization", "Bearer " + oldAccessToken))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post("/api/v1/auth/refresh").cookie(oldRefreshToken))
                    .andExpect(status().isUnauthorized());

            if (refreshResult.status() == 200) {
                assertThat(refreshResult.accessToken()).isNotBlank();
                mvc.perform(get("/api/v1/members/me")
                                .header("Authorization", "Bearer " + refreshResult.accessToken()))
                        .andExpect(status().isUnauthorized());
            }

            MvcResult freshLogin = loginExisting(email, NEW_PASSWORD);
            mvc.perform(get("/api/v1/members/me")
                            .header("Authorization", "Bearer " + accessToken(freshLogin)))
                    .andExpect(status().isOk());
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private ConcurrentResponse concurrentRequest(
            CountDownLatch ready,
            CountDownLatch start,
            java.util.concurrent.Callable<MvcResult> request
    ) {
        ready.countDown();
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시 계정 사건 요청 시작 대기 시간 초과");
            }
            MvcResult result = request.call();
            String accessToken = result.getResponse().getStatus() == 200
                    && result.getResponse().getContentType() != null
                    ? objectMapper.readTree(result.getResponse().getContentAsString())
                            .path("data").path("accessToken").asText(null)
                    : null;
            return new ConcurrentResponse(result.getResponse().getStatus(), accessToken, null);
        } catch (Throwable failure) {
            return new ConcurrentResponse(-1, null, failure);
        }
    }

    @Test
    @DisplayName("V2: 동일 Refresh Token 동시 사용은 정확히 하나만 성공한다")
    void same_refresh_token_concurrent_use_allows_exactly_one_request()
            throws Exception {
        MvcResult login = login(newEmail("refresh-concurrent"));
        String oldAccessToken = accessToken(login);
        Cookie refreshToken = refreshCookie(login);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<RefreshAttempt>> futures = new ArrayList<>();

        Callable<RefreshAttempt> request = () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                return RefreshAttempt.failed(new IllegalStateException(
                        "동시 refresh 시작 신호 대기 시간 초과"
                ));
            }
            try {
                MvcResult result = mvc.perform(post("/api/v1/auth/refresh")
                                .cookie(refreshToken))
                        .andReturn();
                return RefreshAttempt.success(
                        result.getResponse().getStatus(),
                        result.getResponse().getCookie("refreshToken")
                );
            } catch (Throwable failure) {
                return RefreshAttempt.failed(failure);
            }
        };

        try {
            futures.add(executor.submit(request));
            futures.add(executor.submit(request));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<RefreshAttempt> results = new ArrayList<>();
            for (Future<RefreshAttempt> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }

            assertThat(results).allSatisfy(result ->
                    assertThat(result.failure()).isNull()
            );
            assertThat(results.stream().filter(result -> result.status() == 200).toList())
                    .hasSize(1);
            assertThat(results.stream().filter(result -> result.status() == 401).toList())
                    .hasSize(1);
            RefreshAttempt winningAttempt = results.stream()
                    .filter(result -> result.status() == 200)
                    .findFirst().orElseThrow();
            assertThat(winningAttempt.newRefreshToken())
                    .as("동시 refresh 성공 응답은 새 RT를 발급해야 한다")
                    .isNotNull();

            assertThat(refreshToken(refreshToken.getValue()).getConsumedAt()).isNotNull();
            assertThat(refreshToken(refreshToken.getValue()).getRevokedAt()).isNotNull();
            assertThat(refreshToken(winningAttempt.newRefreshToken().getValue()).getRevokedAt())
                    .isNotNull();
            assertThatSessionRevoked(refreshToken.getValue());

            mvc.perform(get("/api/v1/members/me")
                            .header("Authorization", "Bearer " + oldAccessToken))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post("/api/v1/auth/refresh")
                            .cookie(winningAttempt.newRefreshToken()))
                    .andExpect(status().isUnauthorized());
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    @DisplayName("V2: 새 RT 저장 실패 시 RT1 소비가 롤백되어 다시 교체할 수 있다")
    void replacement_insert_failure_rolls_back_consumption() throws Exception {
        MvcResult login = login(newEmail("rotation-rollback"));
        Cookie rt1 = refreshCookie(login);
        doThrow(new IllegalStateException("replacement insert failure"))
                .when(refreshTokenRepository).saveAndFlush(any(RefreshToken.class));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> authService.refresh(rt1.getValue())
        ).isInstanceOf(RuntimeException.class);

        assertThat(refreshToken(rt1.getValue()).getConsumedAt()).isNull();
        assertThat(refreshToken(rt1.getValue()).getRevokedAt()).isNull();

        reset(refreshTokenRepository);
        var retried = authService.refresh(rt1.getValue());
        assertThat(retried.accessToken()).isNotBlank();
        assertThat(retried.refreshToken()).isNotBlank().isNotEqualTo(rt1.getValue());
        assertThat(refreshToken(rt1.getValue()).getConsumedAt()).isNotNull();
    }

    @Test
    @DisplayName("V2: RT 식별 정보 조회 후 재설정이 먼저 커밋되면 이후 회원 잠금 조회에서 오래된 RT를 거부한다")
    void refresh_revalidates_after_password_reset_wins_member_lock() throws Exception {
        String email = newEmail("refresh-reset-order");
        MvcResult login = login(email);
        String oldAccessToken = accessToken(login);
        Cookie rt1 = refreshCookie(login);
        String grant = issueGrant(email, VerificationPurpose.PASSWORD_RESET);
        Long memberId = memberRepository.findByEmail(email).orElseThrow().getId();

        CountDownLatch memberLocked = new CountDownLatch(1);
        CountDownLatch refreshLockRequested = new CountDownLatch(1);
        CountDownLatch allowResetCommit = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> resetFuture = executor.submit(() ->
                    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                        memberRepository.findByEmailForUpdate(email).orElseThrow();
                        memberLocked.countDown();
                        try {
                            if (!refreshLockRequested.await(10, TimeUnit.SECONDS)
                                    || !allowResetCommit.await(10, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("refresh lock ordering timed out");
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(e);
                        }
                        passwordResetService.reset(
                                email, grant, NEW_PASSWORD, NEW_PASSWORD
                        );
                    })
            );
            assertThat(memberLocked.await(10, TimeUnit.SECONDS)).isTrue();

            doAnswer(invocation -> {
                refreshLockRequested.countDown();
                return java.util.Optional.ofNullable(entityManager.find(
                        Member.class,
                        invocation.getArgument(0),
                        LockModeType.PESSIMISTIC_WRITE
                ));
            }).when(memberRepository).findByIdForUpdate(eq(memberId));

            Future<MvcResult> refreshFuture = executor.submit(() ->
                    mvc.perform(post("/api/v1/auth/refresh").cookie(rt1)).andReturn()
            );

            assertThat(refreshLockRequested.await(10, TimeUnit.SECONDS)).isTrue();
            // The RT reference was read before the reset transaction commits. The latch is
            // released immediately before the member-lock query, so the test verifies that
            // the subsequent query observes the committed reset state; it does not assert
            // that the refresh thread was already blocked inside the database lock query.
            allowResetCommit.countDown();

            resetFuture.get(10, TimeUnit.SECONDS);
            MvcResult refreshResult = refreshFuture.get(10, TimeUnit.SECONDS);
            assertThat(refreshResult.getResponse().getStatus()).isEqualTo(401);
            assertThat(refreshToken(rt1.getValue()).getConsumedAt()).isNull();
            assertThat(refreshToken(rt1.getValue()).getRevokedAt()).isNotNull();
            assertThatSessionRevoked(rt1.getValue());

            mvc.perform(get("/api/v1/members/me")
                            .header("Authorization", "Bearer " + oldAccessToken))
                    .andExpect(status().isUnauthorized());
            assertThat(loginExisting(email, NEW_PASSWORD).getResponse().getStatus()).isEqualTo(200);
        } finally {
            allowResetCommit.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private MvcResult login(String email) throws Exception {
        createMemberWithCredential(email, PASSWORD);
        return loginExisting(email, PASSWORD);
    }

    private MvcResult loginExisting(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andReturn();
    }

    private void createMemberWithCredential(String email, String password) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Member member = memberRepository.save(new Member(email, "테스트회원"));
            localCredentialRepository.save(new LocalCredential(
                    member,
                    passwordEncoder.encode(password),
                    LocalDateTime.now()
            ));
        });
    }

    private Member createAdminWithCredential() {
        Member admin = new Member(newEmail("admin"), "관리자");
        ReflectionTestUtils.setField(admin, "role", MemberRole.ADMIN);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            memberRepository.save(admin);
            localCredentialRepository.save(new LocalCredential(
                    admin,
                    passwordEncoder.encode(PASSWORD),
                    LocalDateTime.now()
            ));
        });
        return admin;
    }

    private void resetPassword(String email, String grant, String password)
            throws Exception {
        mvc.perform(post("/api/v1/auth/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "grant", grant,
                                "password", password,
                                "passwordConfirm", password
                        ))))
                .andExpect(status().isOk());
    }

    private Cookie refreshCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie("refreshToken");
        assertThat(cookie).as("응답에 Refresh Token 쿠키가 있어야 한다")
                .isNotNull();
        assertThat(cookie.getValue()).isNotBlank();
        return cookie;
    }

    private long cookieMaxAge(MvcResult result) {
        String header = result.getResponse().getHeader("Set-Cookie");
        assertThat(header).contains("refreshToken=", "Max-Age=");
        String maxAge = header.substring(header.indexOf("Max-Age=") + "Max-Age=".length())
                .split(";", 2)[0];
        return Long.parseLong(maxAge);
    }

    private RefreshToken refreshToken(String rawToken) {
        return refreshTokenRepository.findByTokenHash(hashToken(rawToken)).orElseThrow();
    }

    private String hashToken(String rawToken) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(rawToken.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void assertThatSessionRevoked(String rawToken) {
        Integer revoked = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM auth_session s
                JOIN refresh_token t ON t.auth_session_id = s.id
                WHERE t.token_hash = ? AND s.revoked_at IS NOT NULL
                """, Integer.class, hashToken(rawToken));
        assertThat(revoked).isEqualTo(1);
    }

    private void assertAccessTokenLifetime(String token, LocalDateTime sessionExpiry) {
        Map<String, Object> claims = jwtProvider.payloadOrNull(token);
        long issuedAt = ((Number) claims.get("iat")).longValue();
        long expiresAt = ((Number) claims.get("exp")).longValue();
        assertThat(expiresAt - issuedAt).isLessThanOrEqualTo(300L);
        assertThat(Instant.ofEpochSecond(expiresAt))
                .isBeforeOrEqualTo(sessionExpiry.atZone(clock.getZone()).toInstant());
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

    private IssuedChallenge issueChallengeFromApi(
            String email,
            VerificationPurpose purpose
    ) throws Exception {
        AtomicReference<String> deliveredCode = new AtomicReference<>();
        doAnswer(invocation -> {
            deliveredCode.set(invocation.getArgument(2, String.class));
            return null;
        }).when(mailSender).sendCode(eq(email), eq(purpose), anyString());

        MvcResult response = mvc.perform(post("/api/v1/auth/verification/issue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "purpose", purpose.name()
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        long challengeId = objectMapper.readTree(response.getResponse().getContentAsString())
                .path("data").path("challengeId").asLong();
        assertThat(deliveredCode.get()).isNotBlank();
        return new IssuedChallenge(challengeId, deliveredCode.get());
    }

    private void releaseVerificationBlock(String email, VerificationPurpose purpose) {
        LocalDateTime now = LocalDateTime.now(clock);
        jdbcTemplate.update("""
                UPDATE verification_guard
                SET blocked_until = ?, last_sent_at = ?
                WHERE email = ? AND purpose = ?
                """, now.minusSeconds(1), now.minusSeconds(31), email, purpose.name());
    }

    private String newEmail(String prefix) {
        return "v2-" + prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    private record RefreshAttempt(
            int status,
            Cookie newRefreshToken,
            Throwable failure
    ) {
        private static RefreshAttempt success(int status, Cookie newRefreshToken) {
            return new RefreshAttempt(status, newRefreshToken, null);
        }

        private static RefreshAttempt failed(Throwable failure) {
            return new RefreshAttempt(-1, null, failure);
        }
    }

    private record IssuedChallenge(long challengeId, String deliveredCode) {}

    private record ConcurrentResponse(int status, String accessToken, Throwable failure) {}
}
