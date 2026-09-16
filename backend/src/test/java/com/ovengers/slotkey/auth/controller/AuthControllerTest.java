package com.ovengers.slotkey.auth.controller;

import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.entity.MemberRole;
import com.ovengers.slotkey.member.entity.MemberStatus;
import com.ovengers.slotkey.member.repository.MemberRepository;
import com.ovengers.slotkey.support.MySqlTestContainerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.springframework.test.web.servlet.MvcResult;
import com.ovengers.slotkey.global.security.jwt.JwtUtil;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(MySqlTestContainerConfig.class)
@Transactional
public class AuthControllerTest {
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private MockMvc mvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;
    @Value("${custom.jwt.secret-key}")
    private String secretKey;

    @Autowired
    private EntityManager entityManager;
    @Test
    @DisplayName("회원가입 성공 시 일반 회원으로 저장하고 비밀번호를 해시한다")
    void t1() throws Exception {
        // 1. 가입할 회원 정보 준비
        String email = "signup-test@example.com";
        String password = "Test1234!";
        String nickname = "테스트회원";

        // 2. 회원가입 요청
        ResultActions resultActions = mvc
                .perform(
                        post("/api/v1/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {
                                                    "email": "%s",
                                                    "password": "%s",
                                                    "passwordConfirm": "%s",
                                                    "nickname": "%s"
                                                }
                                                """.formatted(
                                                email,
                                                password,
                                                password,
                                                nickname
                                        )
                                )
                )
                .andDo(print());

        // 3. HTTP 응답 확인
        resultActions
                .andExpect(handler().handlerType(AuthController.class))
                .andExpect(handler().methodName("signup"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.code").value("OK"));

        // 4. 실제 DB 저장 결과 확인
        Member member = memberRepository.findByEmail(email)
                .orElseThrow();

        assertThat(member.getNickname()).isEqualTo(nickname);
        assertThat(member.getRole()).isEqualTo(MemberRole.USER);
        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(member.getCreatedAt()).isNotNull();

        // 원문이 그대로 저장되지 않았는지 확인
        assertThat(member.getPasswordHash()).isNotEqualTo(password);

        // 저장된 해시가 입력한 비밀번호와 일치하는지 확인
        assertThat(
                passwordEncoder.matches(
                        password,
                        member.getPasswordHash()
                )
        ).isTrue();
    }

    @Test
    @DisplayName("이미 가입된 이메일로 회원가입하면 409를 반환한다")
    void t2() throws Exception {
        // 1. 이미 가입된 회원을 직접 저장
        String email = "duplicate-test@example.com";
        String password = "Test1234!";

        Member existingMember = memberRepository.save(
                new Member(
                        email,
                        passwordEncoder.encode(password),
                        "기존회원"
                )
        );

        long memberCountBefore = memberRepository.count();

        // 2. 같은 이메일로 다시 회원가입 요청
        ResultActions resultActions = mvc
                .perform(
                        post("/api/v1/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {
                                                    "email": "%s",
                                                    "password": "%s",
                                                    "passwordConfirm": "%s",
                                                    "nickname": "새회원"
                                                }
                                                """.formatted(
                                                email,
                                                password,
                                                password
                                        )
                                )
                )
                .andDo(print());

        // 3. 이메일 중복 오류 응답 확인
        resultActions
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("FAIL"))
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message")
                        .value("이미 사용 중인 이메일입니다."));

        // 4. 새 회원이 추가되지 않고 기존 회원도 유지되는지 확인
        assertThat(memberRepository.count()).isEqualTo(memberCountBefore);

        Member savedMember = memberRepository.findByEmail(email)
                .orElseThrow();

        assertThat(savedMember.getId()).isEqualTo(existingMember.getId());
        assertThat(savedMember.getNickname()).isEqualTo("기존회원");
    }

    @Test
    @DisplayName("비밀번호와 비밀번호 확인이 다르면 회원가입을 거절한다")
    void t3() throws Exception {
        // 1. 서로 다른 비밀번호 준비
        String email = "password-mismatch@example.com";
        long memberCountBefore = memberRepository.count();

        // 2. 회원가입 요청
        ResultActions resultActions = mvc
                .perform(
                        post("/api/v1/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "email": "%s",
                                            "password": "Test1234!",
                                            "passwordConfirm": "Different1234!",
                                            "nickname": "테스트회원"
                                        }
                                        """.formatted(email))
                )
                .andDo(print());

        // 3. 비밀번호 불일치 오류 확인
        resultActions
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("FAIL"))
                .andExpect(jsonPath("$.code")
                        .value("PASSWORD_CONFIRM_MISMATCH"));

        // 4. 가입이 처리되지 않았는지 확인
        assertThat(memberRepository.existsByEmail(email)).isFalse();
        assertThat(memberRepository.count()).isEqualTo(memberCountBefore);
    }

    @Test
    @DisplayName("로그인하면 액세스 토큰과 24시간 리프레시 쿠키를 발급한다")
    void t4() throws Exception {
        MvcResult result = loginForTest("login-success@example.com");

        String accessToken = extractAccessToken(result);
        assertThat(accessToken).isNotBlank();

        Cookie refreshCookie = result.getResponse()
                .getCookie("refreshToken");

        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.getValue()).isNotBlank();
        assertThat(refreshCookie.isHttpOnly()).isTrue();
        assertThat(refreshCookie.getPath()).isEqualTo("/api/v1/auth");
        assertThat(refreshCookie.getMaxAge()).isEqualTo(86_400);
    }

    @Test
    @DisplayName("비밀번호가 틀리면 로그인을 거절하고 쿠키를 발급하지 않는다")
    void t5() throws Exception {
        String email = "login-failure@example.com";

        memberRepository.save(
                new Member(
                        email,
                        passwordEncoder.encode("Correct1234!"),
                        "테스트회원"
                )
        );

        ResultActions resultActions = mvc
                .perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "email": "%s",
                                            "password": "Wrong1234!"
                                        }
                                        """.formatted(email))
                )
                .andDo(print());

        resultActions
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(cookie().doesNotExist("refreshToken"))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist());
    }

    @Test
    @DisplayName("유효한 리프레시 쿠키로 갱신한 액세스 토큰을 사용할 수 있다")
    void t6() throws Exception {
        String email = "refresh-success@example.com";

        MvcResult loginResult = loginForTest(email);

        Cookie refreshCookie = loginResult.getResponse()
                .getCookie("refreshToken");

        assertThat(refreshCookie).isNotNull();

        MvcResult refreshResult = mvc
                .perform(
                        post("/api/v1/auth/refresh")
                                .cookie(refreshCookie)
                )
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();

        String accessToken = extractAccessToken(refreshResult);

        // 발급된 문자열이 실제 인증에도 사용 가능한지 확인
        mvc.perform(
                        get("/api/v1/members/me")
                                .header(
                                        "Authorization",
                                        "Bearer " + accessToken
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email));
    }

    @Test
    @DisplayName("리프레시 쿠키가 없으면 토큰 갱신을 거절한다")
    void t7() throws Exception {
        mvc.perform(
                        post("/api/v1/auth/refresh")
                )
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    @DisplayName("등록되지 않은 리프레시 토큰으로 갱신하면 거절한다")
    void t8() throws Exception {
        mvc.perform(
                        post("/api/v1/auth/refresh")
                                .cookie(
                                        new Cookie(
                                                "refreshToken",
                                                "unknown-refresh-token"
                                        )
                                )
                )
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    @DisplayName("로그아웃하면 쿠키를 삭제하고 기존 리프레시 토큰의 갱신을 차단한다")
    void t9() throws Exception {
        MvcResult loginResult =
                loginForTest("logout-test@example.com");

        Cookie refreshCookie = loginResult.getResponse()
                .getCookie("refreshToken");

        assertThat(refreshCookie).isNotNull();

        // 로그아웃 전에 원문을 별도로 보관
        String rawRefreshToken = refreshCookie.getValue();

        mvc.perform(
                        post("/api/v1/auth/logout")
                                .cookie(refreshCookie)
                )
                .andDo(print())
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andExpect(cookie().value("refreshToken", ""))
                .andExpect(cookie().maxAge("refreshToken", 0))
                .andExpect(cookie().path("refreshToken", "/api/v1/auth"))
                .andExpect(cookie().httpOnly("refreshToken", true));

        // 브라우저의 쿠키 삭제만 확인하는 것이 아니라,
        // 복사해둔 원문을 다시 보내도 서버가 거절하는지 확인
        mvc.perform(
                        post("/api/v1/auth/refresh")
                                .cookie(
                                        new Cookie(
                                                "refreshToken",
                                                rawRefreshToken
                                        )
                                )
                )
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    @DisplayName("액세스 토큰으로 로그인한 회원 본인의 정보를 조회한다")
    void t10() throws Exception {
        String email = "my-info@example.com";

        MvcResult loginResult = loginForTest(email);
        String accessToken = extractAccessToken(loginResult);

        mvc.perform(
                        get("/api/v1/members/me")
                                .header(
                                        "Authorization",
                                        "Bearer " + accessToken
                                )
                )
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.nickname").value("테스트회원"))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("액세스 토큰 없이 내 정보를 조회하면 거절한다")
    void t11() throws Exception {
        mvc.perform(
                        get("/api/v1/members/me")
                )
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }
    @Test
    @DisplayName("만료된 액세스 토큰으로 내 정보를 조회하면 401을 반환한다")
    void t12() throws Exception {
        String email = "expired-access@example.com";
        loginForTest(email);

        Member member = memberRepository.findByEmail(email)
                .orElseThrow();

        // 기다리지 않고 처음부터 만료된 토큰을 만든다.
        String expiredToken = JwtUtil.createToken(
                secretKey,
                -60_000L,
                Map.of(
                        "id", member.getId(),
                        "email", member.getEmail()
                )
        );

        mvc.perform(
                        get("/api/v1/members/me")
                                .header("Authorization", "Bearer " + expiredToken)
                )
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
    }

    @Test
    @DisplayName("다른 키로 서명한 액세스 토큰으로 접근하면 401을 반환한다")
    void t13() throws Exception {
        String email = "wrong-signature@example.com";
        loginForTest(email);

        Member member = memberRepository.findByEmail(email)
                .orElseThrow();

        // 서버가 사용하는 키와 다른 테스트용 키
        String wrongSecretKey =
                "wrong-signing-key-for-test-only-0123456789abcdef";

        assertThat(wrongSecretKey).isNotEqualTo(secretKey);

        String forgedToken = JwtUtil.createToken(
                wrongSecretKey,
                300_000L,
                Map.of(
                        "id", member.getId(),
                        "email", member.getEmail()
                )
        );

        mvc.perform(
                        get("/api/v1/members/me")
                                .header("Authorization", "Bearer " + forgedToken)
                )
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
    }

    @Test
    @DisplayName("DB에 저장된 리프레시 토큰이 만료되었으면 갱신을 거절한다")
    void t14() throws Exception {
        String email = "expired-refresh@example.com";
        MvcResult loginResult = loginForTest(email);

        Cookie refreshCookie = loginResult.getResponse()
                .getCookie("refreshToken");

        assertThat(refreshCookie).isNotNull();

        Member member = memberRepository.findByEmail(email)
                .orElseThrow();

        // 로그인하면서 저장한 토큰을 DB에 반영한다.
        entityManager.flush();

        // 이번 테스트 회원의 토큰 만료 시각을 과거로 변경한다.
        int updatedCount = entityManager.createQuery("""
                    update RefreshToken r
                    set r.expiresAt = :expiresAt
                    where r.member.id = :memberId
                    """)
                .setParameter("expiresAt", LocalDateTime.of(2000, 1, 1, 0, 0))
                .setParameter("memberId", member.getId())
                .executeUpdate();

        assertThat(updatedCount).isEqualTo(1);

        // 메모리에 남은 기존 엔티티 대신 변경된 DB 값을 읽도록 한다.
        entityManager.clear();

        mvc.perform(
                        post("/api/v1/auth/refresh")
                                .cookie(refreshCookie)
                )
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    @DisplayName("로그인 후 정지된 회원은 기존 액세스 토큰으로 접근할 수 없다")
    void t15() throws Exception {
        String email = "suspended-member@example.com";
        MvcResult loginResult = loginForTest(email);
        String accessToken = extractAccessToken(loginResult);

        entityManager.flush();

        // 정상적으로 로그인한 회원을 이후에 정지시킨 상황
        int updatedCount = entityManager.createQuery("""
                    update Member m
                    set m.status = :status
                    where m.email = :email
                    """)
                .setParameter("status", MemberStatus.SUSPENDED)
                .setParameter("email", email)
                .executeUpdate();

        assertThat(updatedCount).isEqualTo(1);

        entityManager.clear();

        mvc.perform(
                        get("/api/v1/members/me")
                                .header("Authorization", "Bearer " + accessToken)
                )
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_INACTIVE"));
    }

    @Test
    @DisplayName("일반 회원이 관리자 공간 등록 API에 접근하면 403을 반환한다")
    void t16() throws Exception {
        MvcResult loginResult = loginForTest("user-admin-access@example.com");
        String accessToken = extractAccessToken(loginResult);

        mvc.perform(
                        post("/api/v1/admin/spaces")
                                .header("Authorization", "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                )
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    // 회원 저장 후 실제 로그인 API를 호출한다.
    private MvcResult loginForTest(String email) throws Exception {
        String password = "Test1234!";

        memberRepository.save(
                new Member(
                        email,
                        passwordEncoder.encode(password),
                        "테스트회원"
                )
        );

        return mvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "email": "%s",
                                            "password": "%s"
                                        }
                                        """.formatted(email, password))
                )
                .andExpect(status().isOk())
                .andReturn();
    }
    // 로그인·갱신 응답에서 액세스 토큰을 꺼낸다.
    private String extractAccessToken(MvcResult result) throws Exception {
        String responseBody = result.getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        String accessToken = objectMapper.readTree(responseBody)
                .path("data")
                .path("accessToken")
                .asText();

        assertThat(accessToken).isNotBlank();

        return accessToken;
    }
}

