package com.ovengers.slotkey.auth.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ovengers.slotkey.auth.entity.LocalCredential;
import com.ovengers.slotkey.auth.entity.VerificationPurpose;
import com.ovengers.slotkey.auth.mail.VerificationMailSender;
import com.ovengers.slotkey.auth.service.VerificationChallengeService;
import com.ovengers.slotkey.auth.service.VerificationCheckService;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.entity.MemberRole;
import com.ovengers.slotkey.member.entity.MemberStatus;
import com.ovengers.slotkey.member.repository.MemberRepository;
import com.ovengers.slotkey.auth.repository.LocalCredentialRepository;
import com.ovengers.slotkey.support.MySqlTestContainerConfig;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class V2SecurityRedTest {

    private static final String PASSWORD = "Test1234!";
    private static final String NEW_PASSWORD = "New1234!";

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

    @Autowired
    private PlatformTransactionManager transactionManager;

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

    @Disabled("V2 구현 전까지 CI에서 비활성화")
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

    @Disabled("V2 구현 전까지 CI에서 비활성화")
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

    @Disabled("V2 구현 전까지 CI에서 비활성화")
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

    @Disabled("V2 구현 전까지 CI에서 비활성화")
    @Test
    @DisplayName("V2: 정상 사용자는 RT Rotation 후 새 RT로 계속 갱신할 수 있다")
    void refresh_token_rotation_allows_next_refresh_with_new_token() throws Exception {
        String email = newEmail("rotation-success");
        MvcResult login = login(email);
        Cookie rt1 = refreshCookie(login);

        MvcResult firstRefresh = mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(rt1))
                .andExpect(status().isOk())
                .andReturn();

        Cookie rt2 = refreshCookie(firstRefresh);
        assertThat(rt2).as("첫 refresh 응답은 RT2를 발급해야 한다")
                .isNotNull();
        assertThat(rt2.getValue()).isNotBlank().isNotEqualTo(rt1.getValue());

        MvcResult secondRefresh = mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(rt2))
                .andExpect(status().isOk())
                .andReturn();

        Cookie rt3 = refreshCookie(secondRefresh);
        assertThat(rt3).as("두 번째 refresh 응답은 RT3를 발급해야 한다")
                .isNotNull();
        assertThat(rt3.getValue()).isNotBlank().isNotEqualTo(rt2.getValue());
    }

    @Disabled("V2 구현 전까지 CI에서 비활성화")
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

    @Disabled("V2 구현 전까지 CI에서 비활성화")
    @Test
    @DisplayName("V2: 비밀번호 재설정은 여러 로그인 세션의 AT와 RT를 모두 폐기한다")
    void password_reset_invalidates_all_existing_sessions() throws Exception {
        String email = newEmail("password-reset-sessions");
        createMemberWithCredential(email, PASSWORD);

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
    }

    @Disabled("V2 구현 전까지 CI에서 비활성화")
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
    }

    @Disabled("V2 구현 전까지 CI에서 비활성화")
    @Test
    @DisplayName("V2: 회원탈퇴 후 기존 Access Token은 보호 API에서 거부된다")
    void withdrawal_invalidates_existing_access_token() throws Exception {
        String email = newEmail("withdrawal");
        MvcResult login = login(email);
        String accessToken = accessToken(login);

        mvc.perform(post("/api/v1/auth/withdraw")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", PASSWORD
                        ))))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/members/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    @Disabled("V2 구현 전까지 CI에서 비활성화")
    @Test
    @DisplayName("V2: 동일 Refresh Token 동시 사용은 정확히 하나만 성공한다")
    void same_refresh_token_concurrent_use_allows_exactly_one_request()
            throws Exception {
        Cookie refreshToken = refreshCookie(login(newEmail("refresh-concurrent")));
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
            assertThat(results.stream()
                    .filter(result -> result.status() == 200)
                    .findFirst().orElseThrow().newRefreshToken())
                    .as("동시 refresh 성공 응답은 새 RT를 발급해야 한다")
                    .isNotNull();
        } finally {
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
}
