package com.ovengers.slotkey.audit.controller;

import com.ovengers.slotkey.audit.entity.AuditAction;
import com.ovengers.slotkey.audit.entity.AuditLog;
import com.ovengers.slotkey.audit.entity.AuditTargetType;
import com.ovengers.slotkey.audit.repository.AuditLogRepository;
import com.ovengers.slotkey.global.security.jwt.JwtProvider;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.entity.MemberRole;
import com.ovengers.slotkey.member.entity.MemberStatus;
import com.ovengers.slotkey.member.repository.MemberRepository;
import com.ovengers.slotkey.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class AdminAuditLogSecurityIntegrationTest extends IntegrationTestSupport {

    private static final String ADMIN_AUDIT_LOGS_PATH = "/api/v1/admin/audit-logs";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("미인증 요청은 관리자 감사 로그를 조회할 수 없고 401 AUTHENTICATION_REQUIRED를 반환한다")
    void getAuditLogs_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(ADMIN_AUDIT_LOGS_PATH)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("USER 역할은 관리자 감사 로그를 조회할 수 없고 403 ACCESS_DENIED를 반환한다")
    void getAuditLogs_userRole_returns403() throws Exception {
        Member user = saveMember(MemberRole.USER, MemberStatus.ACTIVE);

        mockMvc.perform(get(ADMIN_AUDIT_LOGS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("이미 발급된 ADMIN Access Token은 회원 DB 상태가 비활성(SUSPENDED, WITHDRAWN)으로 변경되어도 만료 전까지 관리자 감사 로그를 정상 조회할 수 있다 (#208 무조회 계약)")
    void getAuditLogs_inactiveAdmin_validJwt_returns200() throws Exception {
        for (MemberStatus status : new MemberStatus[]{MemberStatus.SUSPENDED, MemberStatus.WITHDRAWN}) {
            // 1. ACTIVE 상태의 ADMIN 생성 및 저장
            Member admin = saveMember(MemberRole.ADMIN, MemberStatus.ACTIVE);

            // 2. ACTIVE ADMIN 상태에서 Access Token 먼저 발급
            String token = bearerToken(admin);

            // 3. 같은 회원 행의 DB 상태를 SUSPENDED 또는 WITHDRAWN으로 변경 후 flush/clear
            admin.updateStatus(status);
            memberRepository.saveAndFlush(admin);
            entityManager.clear();

            // 4. 상태 변경 전 발급받은 동일한 토큰으로 조회 시 200 OK 검증
            mockMvc.perform(get(ADMIN_AUDIT_LOGS_PATH)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUCCESS"));
        }
    }

    @Test
    @DisplayName("ACTIVE ADMIN은 관리자 감사 로그를 정상 조회(200 OK)할 수 있으며 조회 자체가 새 감사 로그를 남기지 않는다")
    void getAuditLogs_activeAdmin_returns200AndDoesNotCreateAuditLog() throws Exception {
        Member admin = saveMember(MemberRole.ADMIN, MemberStatus.ACTIVE);

        // 기존 감사 로그 1건 저장
        AuditLog existingLog = AuditLog.builder()
                .actorMemberId(admin.getId())
                .action(AuditAction.REGISTER_SPACE)
                .targetType(AuditTargetType.SPACE)
                .targetId(10L)
                .reason("초기 공간 등록")
                .beforeValue(null)
                .afterValue("{\"name\":\"테스트 공간\"}")
                .createdAt(LocalDateTime.now())
                .build();
        auditLogRepository.saveAndFlush(existingLog);

        long auditCountBefore = auditLogRepository.count();

        // when & then
        mockMvc.perform(get(ADMIN_AUDIT_LOGS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(admin))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].action").value("REGISTER_SPACE"))
                .andExpect(jsonPath("$.data.content[0].targetType").value("SPACE"))
                .andExpect(jsonPath("$.data.content[0].targetId").value(10L));

        long auditCountAfter = auditLogRepository.count();
        assertThat(auditCountAfter).isEqualTo(auditCountBefore);
    }

    private Member saveMember(MemberRole role, MemberStatus status) {
        Member member = new Member(
                role.name().toLowerCase() + "-" + status.name().toLowerCase() + "-" + System.nanoTime() + "@test.com",
                "encoded-password",
                "security-test-member"
        );
        ReflectionTestUtils.setField(member, "role", role);
        ReflectionTestUtils.setField(member, "status", status);
        return memberRepository.saveAndFlush(member);
    }

    private String bearerToken(Member member) {
        return "Bearer " + jwtProvider.genAccessToken(member);
    }
}
