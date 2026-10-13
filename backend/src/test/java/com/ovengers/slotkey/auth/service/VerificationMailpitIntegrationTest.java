package com.ovengers.slotkey.auth.service;

import com.ovengers.slotkey.auth.dto.internal.ChallengeIssueResult;
import com.ovengers.slotkey.auth.entity.VerificationChallenge;
import com.ovengers.slotkey.auth.entity.VerificationPurpose;
import com.ovengers.slotkey.auth.repository.VerificationChallengeRepository;
import com.ovengers.slotkey.support.MySqlTestContainerConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.mail.host=localhost",
        "spring.mail.port=1025",
        "spring.mail.default-encoding=UTF-8",
        "spring.mail.properties[mail.smtp.auth]=false",
        "spring.mail.properties[mail.smtp.starttls.enable]=false",
        "spring.mail.properties[mail.smtp.connectiontimeout]=3000",
        "spring.mail.properties[mail.smtp.timeout]=3000",
        "spring.mail.properties[mail.smtp.writetimeout]=3000",
        "app.verification.mail-from=noreply@slotkey.test"
})
@ActiveProfiles("test")
@Import(MySqlTestContainerConfig.class)
@EnabledIfEnvironmentVariable(
        named = "SLOTKEY_MAILPIT_TEST",
        matches = "true"
)
class VerificationMailpitIntegrationTest {

    @Autowired
    private VerificationChallengeService challengeService;

    @Autowired
    private VerificationChallengeRepository challengeRepository;

    @Test
    void 인증번호를_저장하고_Mailpit으로_발송한다() {
        String email = "mailpit-" + UUID.randomUUID()
                + "@example.com";

        ChallengeIssueResult result = challengeService.issue(
                email,
                VerificationPurpose.SIGNUP
        );

        VerificationChallenge saved = challengeRepository
                .findById(result.challengeId())
                .orElseThrow();

        assertThat(saved.getCodeHash()).hasSize(64);
        assertThat(saved.getVerifiedAt()).isNull();
        assertThat(saved.getInvalidatedAt()).isNull();

        assertThat(saved.getExpiresAt())
                .isEqualTo(saved.getIssuedAt().plusMinutes(3));

        assertThat(result.resendAvailableAt())
                .isEqualTo(saved.getIssuedAt().plusSeconds(30));
    }
}