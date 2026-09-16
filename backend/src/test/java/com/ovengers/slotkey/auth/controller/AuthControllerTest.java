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

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(MySqlTestContainerConfig.class)
@Transactional
public class AuthControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

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
}