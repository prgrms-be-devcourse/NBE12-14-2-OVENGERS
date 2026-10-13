package com.ovengers.slotkey.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ovengers.slotkey.auth.entity.VerificationPurpose;
import com.ovengers.slotkey.auth.mail.VerificationMailSender;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(MySqlTestContainerConfig.class)
class VerificationControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private VerificationMailSender mailSender;

    @Test
    void 오류_응답을_반환해도_오입력이_누적되어_다섯번째에_차단된다()
            throws Exception {
        String email = "http-" + UUID.randomUUID() + "@example.com";
        AtomicReference<String> deliveredCode = new AtomicReference<>();

        doAnswer(invocation -> {
            deliveredCode.set(invocation.getArgument(2, String.class));
            return null;
        }).when(mailSender).sendCode(
                eq(email),
                eq(VerificationPurpose.SIGNUP),
                anyString()
        );

        var issued = mvc.perform(
                        post("/api/v1/auth/verification/issue")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        Map.of(
                                                "email", email,
                                                "purpose", "SIGNUP"
                                        )
                                ))
                )
                .andExpect(status().isOk())
                .andReturn();

        long challengeId = objectMapper.readTree(
                issued.getResponse().getContentAsString()
        ).path("data").path("challengeId").asLong();

        String wrongCode = deliveredCode.get().equals("00000000")
                ? "00000001"
                : "00000000";

        String body = objectMapper.writeValueAsString(Map.of(
                "email", email,
                "purpose", "SIGNUP",
                "challengeId", challengeId,
                "code", wrongCode
        ));

        for (int i = 0; i < 4; i++) {
            mvc.perform(
                            post("/api/v1/auth/verification/check")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body)
                    )
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code")
                            .value("VERIFICATION_CODE_MISMATCH"));
        }

        mvc.perform(
                        post("/api/v1/auth/verification/check")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code")
                        .value("VERIFICATION_TEMPORARILY_BLOCKED"));

        // 차단은 번호 확인뿐 아니라 재발송에도 적용된다.
        mvc.perform(
                        post("/api/v1/auth/verification/issue")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        Map.of(
                                                "email", email,
                                                "purpose", "SIGNUP"
                                        )
                                ))
                )
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code")
                        .value("VERIFICATION_TEMPORARILY_BLOCKED"));
    }
}