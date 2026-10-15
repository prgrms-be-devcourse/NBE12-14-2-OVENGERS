package com.ovengers.slotkey.auth.service;

import com.ovengers.slotkey.audit.entity.AuditAction;
import com.ovengers.slotkey.audit.entity.AuditTargetType;
import com.ovengers.slotkey.audit.repository.AuditLogRepository;
import com.ovengers.slotkey.auth.dto.internal.ChallengeIssueResult;
import com.ovengers.slotkey.auth.dto.internal.VerificationCheckResult;
import com.ovengers.slotkey.auth.entity.VerificationPurpose;
import com.ovengers.slotkey.auth.mail.VerificationMailSender;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.entity.MemberStatus;
import com.ovengers.slotkey.member.repository.MemberRepository;
import com.ovengers.slotkey.member.service.AdminMemberService;
import com.ovengers.slotkey.member.service.MemberService;
import com.ovengers.slotkey.support.MySqlTestContainerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static com.ovengers.slotkey.auth.dto.internal.VerificationCheckResult.Status.ADMIN_LOCKED;
import static com.ovengers.slotkey.auth.dto.internal.VerificationCheckResult.Status.TEMPORARILY_BLOCKED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Import(MySqlTestContainerConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class VerificationMemberStatusIntegrationTest {

    @Autowired
    private VerificationChallengeService challengeService;

    @Autowired
    private VerificationCheckService checkService;

    @Autowired
    private MemberService memberService;

    @Autowired
    private AdminMemberService adminMemberService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @MockBean
    private VerificationMailSender mailSender;

    @MockBean
    private Clock clock;

    private final AtomicReference<Instant> currentTime = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        currentTime.set(Instant.parse("2026-10-14T00:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        when(clock.instant()).thenAnswer(invocation -> currentTime.get());
    }

    @Test
    void 세번째_인증차단이면_기존_ACTIVE_회원을_정지하고_시스템_감사로그를_남긴다() {
        String email = newEmail();
        Member member = createMember(email);

        VerificationCheckResult firstBlock = failFiveTimes(email);
        assertThat(firstBlock.status()).isEqualTo(TEMPORARILY_BLOCKED);
        assertMemberStatus(member.getId(), MemberStatus.ACTIVE);
        assertVerificationLockAuditCount(member.getId(), 0);

        currentTime.updateAndGet(time -> time.plusSeconds(5 * 60L + 30));
        VerificationCheckResult secondBlock = failFiveTimes(email);
        assertThat(secondBlock.status()).isEqualTo(TEMPORARILY_BLOCKED);
        assertMemberStatus(member.getId(), MemberStatus.ACTIVE);
        assertVerificationLockAuditCount(member.getId(), 0);

        currentTime.updateAndGet(time -> time.plusSeconds(60 * 60L + 30));
        VerificationCheckResult thirdBlock = failFiveTimes(email);
        assertThat(thirdBlock.status()).isEqualTo(ADMIN_LOCKED);

        assertMemberStatus(member.getId(), MemberStatus.SUSPENDED);
        assertVerificationLockAuditCount(member.getId(), 1);

        var logs = auditLogRepository.searchAuditLogs(
                null,
                AuditAction.LOCK_MEMBER_BY_VERIFICATION,
                AuditTargetType.MEMBER,
                member.getId(),
                null,
                null,
                org.springframework.data.domain.PageRequest.of(0, 10)
        );

        assertThat(logs.getTotalElements()).isEqualTo(1);
        var log = logs.getContent().get(0);
        assertThat(log.getActorMemberId()).isNull();
        assertThat(log.getBeforeValue()).isEqualTo("\"ACTIVE\"");
        assertThat(log.getAfterValue()).isEqualTo("\"SUSPENDED\"");
    }

    private void assertMemberStatus(Long memberId, MemberStatus expectedStatus) {
        assertThat(memberRepository.findById(memberId).orElseThrow().getStatus())
                .isEqualTo(expectedStatus);
    }

    private void assertVerificationLockAuditCount(Long memberId, long expectedCount) {
        var logs = auditLogRepository.searchAuditLogs(
                null,
                AuditAction.LOCK_MEMBER_BY_VERIFICATION,
                AuditTargetType.MEMBER,
                memberId,
                null,
                null,
                org.springframework.data.domain.PageRequest.of(0, 10)
        );

        assertThat(logs.getTotalElements()).isEqualTo(expectedCount);
    }

    @Test
    void 관리자가_회원복구하면_해당_이메일의_인증잠금도_해제된다() {
        String email = newEmail();
        Member member = createMember(email);

        failFiveTimes(email);
        currentTime.updateAndGet(time -> time.plusSeconds(5 * 60L + 30));
        failFiveTimes(email);
        currentTime.updateAndGet(time -> time.plusSeconds(60 * 60L + 30));
        assertThat(failFiveTimes(email).status()).isEqualTo(ADMIN_LOCKED);

        adminMemberService.restore(member.getId(), "인증 잠금 해제", 999L);

        assertThat(memberRepository.findById(member.getId()).orElseThrow().getStatus())
                .isEqualTo(MemberStatus.ACTIVE);

        currentTime.updateAndGet(time -> time.plusSeconds(30));
        AtomicReference<String> deliveredCode = new AtomicReference<>();
        doAnswer(invocation -> {
            deliveredCode.set(invocation.getArgument(2, String.class));
            return null;
        }).when(mailSender).sendCode(
                eq(email), eq(VerificationPurpose.PASSWORD_RESET), anyString()
        );

        ChallengeIssueResult issued = challengeService.issue(
                email, VerificationPurpose.PASSWORD_RESET
        );
        assertThat(deliveredCode).isNotNull();
        assertThat(issued.challengeId()).isNotNull();
    }

    private Member createMember(String email) {
        String signupGrant = issueGrant(email, VerificationPurpose.SIGNUP);
        return memberService.signup(
                email,
                "Test1234!",
                "Test1234!",
                "테스트회원",
                signupGrant
        );
    }

    private VerificationCheckResult failFiveTimes(String email) {
        AtomicReference<String> deliveredCode = new AtomicReference<>();
        doAnswer(invocation -> {
            deliveredCode.set(invocation.getArgument(2, String.class));
            return null;
        }).when(mailSender).sendCode(
                eq(email), eq(VerificationPurpose.PASSWORD_RESET), anyString()
        );

        ChallengeIssueResult issued = challengeService.issue(
                email, VerificationPurpose.PASSWORD_RESET
        );
        String wrongCode = deliveredCode.get().equals("00000000")
                ? "00000001"
                : "00000000";
        VerificationCheckResult result = null;
        for (int i = 0; i < 5; i++) {
            result = checkService.verify(
                    email,
                    VerificationPurpose.PASSWORD_RESET,
                    issued.challengeId(),
                    wrongCode
            );
        }
        return result;
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

    private String newEmail() {
        return "status-link-" + UUID.randomUUID() + "@example.com";
    }
}
