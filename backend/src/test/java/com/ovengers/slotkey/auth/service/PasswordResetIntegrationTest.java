package com.ovengers.slotkey.auth.service;

import com.ovengers.slotkey.auth.entity.LocalCredential;
import com.ovengers.slotkey.auth.entity.VerificationPurpose;
import com.ovengers.slotkey.auth.mail.VerificationMailSender;
import com.ovengers.slotkey.auth.repository.LocalCredentialRepository;
import com.ovengers.slotkey.auth.repository.VerificationGrantRepository;
import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.support.MySqlTestContainerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
@ActiveProfiles("test")
@Import(MySqlTestContainerConfig.class)
class PasswordResetIntegrationTest {

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private com.ovengers.slotkey.member.service.MemberService memberService;

    @Autowired
    private VerificationChallengeService challengeService;

    @Autowired
    private VerificationCheckService checkService;

    @Autowired
    private LocalCredentialRepository localCredentialRepository;

    @Autowired
    private VerificationGrantRepository grantRepository;

    @Autowired
    private VerificationSecretHasher secretHasher;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private VerificationMailSender mailSender;

    @Test
    void 재설정_증표로_로컬_비밀번호를_변경하고_증표를_소비한다() {
        String email = newEmail();
        Member member = createMember(email, "Old1234!");
        String grant = issueGrant(email, VerificationPurpose.PASSWORD_RESET);

        passwordResetService.reset(
                email, grant, "New1234!", "New1234!"
        );

        LocalCredential credential = localCredentialRepository
                .findById(member.getId())
                .orElseThrow();
        assertThat(passwordEncoder.matches("Old1234!", credential.getPasswordHash()))
                .isFalse();
        assertThat(passwordEncoder.matches("New1234!", credential.getPasswordHash()))
                .isTrue();
        assertThat(grantRepository.findByGrantHash(secretHasher.hashGrant(grant))
                .orElseThrow().getConsumedAt()).isNotNull();
    }

    @Test
    void 비밀번호_확인_실패_후_같은_재설정_증표로_재시도할_수_있다() {
        String email = newEmail();
        Member member = createMember(email, "Old1234!");
        String grant = issueGrant(email, VerificationPurpose.PASSWORD_RESET);

        assertThatThrownBy(() -> passwordResetService.reset(
                email, grant, "New1234!", "Different1234!"
        )).isInstanceOf(BusinessException.class);

        assertThat(grantRepository.findByGrantHash(secretHasher.hashGrant(grant))
                .orElseThrow().getConsumedAt()).isNull();
        String originalHash = localCredentialRepository.findById(member.getId())
                .orElseThrow().getPasswordHash();

        passwordResetService.reset(
                email, grant, "New1234!", "New1234!"
        );

        assertThat(localCredentialRepository.findById(member.getId())
                .orElseThrow().getPasswordHash()).isNotEqualTo(originalHash);
    }

    @Test
    void 가입용_증표로는_비밀번호를_재설정할_수_없다() {
        String email = newEmail();
        createMember(email, "Old1234!");
        String signupGrant = issueGrant(newEmail(), VerificationPurpose.SIGNUP);

        assertThatThrownBy(() -> passwordResetService.reset(
                email, signupGrant, "New1234!", "New1234!"
        )).isInstanceOf(BusinessException.class);
    }

    private Member createMember(String email, String rawPassword) {
        String signupGrant = issueGrant(email, VerificationPurpose.SIGNUP);
        return memberService.signup(
                email,
                rawPassword,
                rawPassword,
                "테스트회원",
                signupGrant
        );
    }

    private String issueGrant(String email, VerificationPurpose purpose) {
        AtomicReference<String> deliveredCode = new AtomicReference<>();

        doAnswer(invocation -> {
            deliveredCode.set(invocation.getArgument(2, String.class));
            return null;
        }).when(mailSender).sendCode(
                eq(email), eq(purpose), anyString()
        );

        var issued = challengeService.issue(email, purpose);
        var checked = checkService.verify(
                email, purpose, issued.challengeId(), deliveredCode.get()
        );
        return checked.grant();
    }

    private String newEmail() {
        return "reset-" + UUID.randomUUID() + "@example.com";
    }
}
