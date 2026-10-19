package com.ovengers.slotkey.auth.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ovengers.slotkey.auth.entity.LocalCredential;
import com.ovengers.slotkey.auth.repository.LocalCredentialRepository;
import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.repository.MemberRepository;
import com.ovengers.slotkey.support.MySqlTestContainerConfig;
import com.ovengers.slotkey.support.SettableClockConfig;
import com.ovengers.slotkey.support.SettableClockConfig.SettableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import({MySqlTestContainerConfig.class, SettableClockConfig.class})
class LoginRateLimitIntegrationTest {

    private static final String PASSWORD = "Test1234!";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MemberRepository memberRepository;
    @Autowired private LocalCredentialRepository credentialRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private SettableClock clock;
    @Autowired private LoginRateLimitService rateLimitService;

    @BeforeEach
    void setClock() {
        clock.set(ZonedDateTime.of(2027, 3, 1, 0, 1, 0, 0, SettableClockConfig.KST));
    }

    @Test
    void unknownEmailGetsSameTenAttemptsAndEleventhIsLimited() throws Exception {
        String email = newEmail();
        for (int i = 0; i < 10; i++) {
            login(email, "Wrong1234!").andExpect(status().isUnauthorized());
        }
        login(email, "Wrong1234!")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_RATE_LIMITED"));
    }

    @Test
    void separateEmailsHaveIndependentBuckets() throws Exception {
        String first = newEmail();
        String second = newEmail();
        for (int i = 0; i < 10; i++) {
            login(first, "Wrong1234!").andExpect(status().isUnauthorized());
        }
        login(first, "Wrong1234!").andExpect(status().isTooManyRequests());
        login(second, "Wrong1234!").andExpect(status().isUnauthorized());
    }

    @Test
    void nextFixedWindowIsAllowedAndRetryAfterRoundsUpToBoundary() throws Exception {
        String email = newEmail();
        for (int i = 0; i < 10; i++) {
            login(email, "Wrong1234!").andExpect(status().isUnauthorized());
        }
        clock.set(ZonedDateTime.of(2027, 3, 1, 0, 14, 59, 999_000_000,
                SettableClockConfig.KST));
        login(email, "Wrong1234!")
                .andExpect(status().isTooManyRequests())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Retry-After", "1"));

        clock.set(ZonedDateTime.of(2027, 3, 1, 0, 15, 0, 0, SettableClockConfig.KST));
        login(email, "Wrong1234!").andExpect(status().isUnauthorized());
    }

    @Test
    void malformedAndInvalidDtoRequestsAreRejectedBeforeTheLimiter() throws Exception {
        String email = newEmail();
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isBadRequest());
        for (int i = 0; i < 10; i++) {
            login(email, "Wrong1234!").andExpect(status().isUnauthorized());
        }
        login(email, "Wrong1234!").andExpect(status().isTooManyRequests());
    }

    @Test
    void simultaneousFirstRequestsConsumeExactlyTenSlots() throws Exception {
        String email = newEmail();
        int requestCount = 12;
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        List<Future<Integer>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < requestCount; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("start barrier timed out");
                    }
                    return login(email, "Wrong1234!").andReturn().getResponse().getStatus();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(30, TimeUnit.SECONDS));
            }
            assertThat(statuses).filteredOn(status -> status == 401).hasSize(10);
            assertThat(statuses).filteredOn(status -> status == 429).hasSize(2);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void databaseFailureFailsClosedAsAuthenticationServiceUnavailable() {
        LoginRateLimitService unavailable = new LoginRateLimitService(
                (email, windowId, limit) -> {
                    throw new org.springframework.dao.DataAccessResourceFailureException(
                            "unavailable");
                }, clock
        );
        assertThatThrownBy(() -> unavailable.acquire(newEmail()))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.AUTHENTICATION_SERVICE_UNAVAILABLE));
    }

    @Test
    void rateLimitingDoesNotSuspendMemberOrRevokeExistingSession() throws Exception {
        String email = newEmail();
        createMember(email);
        MvcResult successful = login(email, PASSWORD).andExpect(status().isOk()).andReturn();
        String token = objectMapper.readTree(successful.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
        Member member = memberRepository.findByEmail(email).orElseThrow();
        Integer sessionCountBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth_session WHERE member_id = ? AND revoked_at IS NULL",
                Integer.class, member.getId());

        for (int i = 1; i < 9; i++) {
            login(email, "Wrong1234!").andExpect(status().isUnauthorized());
        }
        login(email, "Wrong1234!").andExpect(status().isUnauthorized());
        login(email, "Wrong1234!").andExpect(status().isTooManyRequests());

        mvc.perform(get("/api/v1/members/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(memberRepository.findById(member.getId()).orElseThrow().getStatus().name())
                .isEqualTo("ACTIVE");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth_session WHERE member_id = ? AND revoked_at IS NULL",
                Integer.class, member.getId())).isEqualTo(sessionCountBefore);
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password)
            throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))));
    }

    private void createMember(String email) {
        transactionTemplate.executeWithoutResult(status -> {
            Member member = memberRepository.save(new Member(email, "rate-limit-test"));
            credentialRepository.save(new LocalCredential(
                    member, passwordEncoder.encode(PASSWORD),
                    LocalDateTime.of(2027, 3, 1, 0, 1)
            ));
        });
    }

    private String newEmail() {
        return "login-limit-" + UUID.randomUUID() + "@example.com";
    }
}
