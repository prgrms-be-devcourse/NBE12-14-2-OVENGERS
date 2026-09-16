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
}