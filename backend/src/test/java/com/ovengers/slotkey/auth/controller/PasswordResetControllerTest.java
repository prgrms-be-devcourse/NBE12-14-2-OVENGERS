package com.ovengers.slotkey.auth.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ovengers.slotkey.auth.entity.VerificationPurpose;
import com.ovengers.slotkey.auth.mail.VerificationMailSender;
import com.ovengers.slotkey.auth.service.VerificationChallengeService;
import com.ovengers.slotkey.auth.service.VerificationCheckService;
import com.ovengers.slotkey.member.service.MemberService;
import com.ovengers.slotkey.support.MySqlTestContainerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

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

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(MySqlTestContainerConfig.class)
class PasswordResetControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MemberService memberService;

    @Autowired
    private VerificationChallengeService challengeService;

    @Autowired
    private VerificationCheckService checkService;

    @MockBean
    private VerificationMailSender mailSender;

    @Test
    void 비로그인_비밀번호_재설정_API가_동작하고_V1에서는_기존_AT가_즉시_폐기되지_않는다()
            throws Exception {
        String email = "reset-http-" + UUID.randomUUID() + "@example.com";
        String signupGrant = issueGrant(email, VerificationPurpose.SIGNUP);
        memberService.signup(
                email, "Old1234!", "Old1234!", "테스트회원", signupGrant
        );

        String accessToken = login(email, "Old1234!");
        String resetGrant = issueGrant(email, VerificationPurpose.PASSWORD_RESET);

        mvc.perform(post("/api/v1/auth/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "grant", resetGrant,
                                "password", "New1234!",
                                "passwordConfirm", "New1234!"
                        ))))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/members/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    private String login(String email, String password) throws Exception {
        String response = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode data = objectMapper.readTree(response).path("data");
        String accessToken = data.path("accessToken").asText();
        assertThat(accessToken).isNotBlank();
        return accessToken;
    }

    private String issueGrant(String email, VerificationPurpose purpose)
            throws Exception {
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
}
