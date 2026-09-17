package com.ovengers.slotkey.space.controller;

import com.ovengers.slotkey.audit.entity.AuditAction;
import com.ovengers.slotkey.audit.repository.AuditLogRepository;
import com.ovengers.slotkey.global.security.jwt.JwtProvider;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.entity.MemberRole;
import com.ovengers.slotkey.member.entity.MemberStatus;
import com.ovengers.slotkey.member.repository.MemberRepository;
import com.ovengers.slotkey.space.repository.SpaceRepository;
import com.ovengers.slotkey.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@Transactional
class AdminSpaceSecurityIntegrationTest extends IntegrationTestSupport {

    private static final String ADMIN_SPACES_PATH = "/api/v1/admin/spaces";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private SpaceRepository spaceRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private JwtProvider jwtProvider;

    @Test
    @DisplayName("미인증 요청은 관리자 공간 등록을 할 수 없고 데이터와 감사 로그를 남기지 않는다")
    void createSpace_unauthenticated_returns401WithoutSideEffects() throws Exception {
        long spacesBefore = spaceRepository.count();
        long auditsBefore = auditLogRepository.count();

        mockMvc.perform(post(ADMIN_SPACES_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        assertThat(spaceRepository.count()).isEqualTo(spacesBefore);
        assertThat(auditLogRepository.count()).isEqualTo(auditsBefore);
    }

    @Test
    @DisplayName("USER 역할은 관리자 공간 등록을 할 수 없고 데이터와 감사 로그를 남기지 않는다")
    void createSpace_userRole_returns403WithoutSideEffects() throws Exception {
        Member user = saveMember(MemberRole.USER, MemberStatus.ACTIVE);
        long spacesBefore = spaceRepository.count();
        long auditsBefore = auditLogRepository.count();

        mockMvc.perform(post(ADMIN_SPACES_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateRequest()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertThat(spaceRepository.count()).isEqualTo(spacesBefore);
        assertThat(auditLogRepository.count()).isEqualTo(auditsBefore);
    }

    @Test
    @DisplayName("정지 및 탈퇴 회원 토큰은 관리자 공간 등록 전에 ACCOUNT_INACTIVE로 차단된다")
    void createSpace_inactiveMember_returns403WithoutSideEffects() throws Exception {
        for (MemberStatus status : new MemberStatus[]{MemberStatus.SUSPENDED, MemberStatus.WITHDRAWN}) {
            Member inactiveMember = saveMember(MemberRole.ADMIN, status);
            long spacesBefore = spaceRepository.count();
            long auditsBefore = auditLogRepository.count();

            mockMvc.perform(post(ADMIN_SPACES_PATH)
                            .header(HttpHeaders.AUTHORIZATION, bearerToken(inactiveMember))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validCreateRequest()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCOUNT_INACTIVE"));

            assertThat(spaceRepository.count()).isEqualTo(spacesBefore);
            assertThat(auditLogRepository.count()).isEqualTo(auditsBefore);
        }
    }

    @Test
    @DisplayName("ACTIVE ADMIN은 공간을 등록 및 수정하고 각 작업의 감사 로그를 남긴다")
    void createAndUpdateSpace_activeAdmin_succeedsAndWritesAuditLogs() throws Exception {
        Member admin = saveMember(MemberRole.ADMIN, MemberStatus.ACTIVE);
        String authorization = bearerToken(admin);

        String createResponse = mockMvc.perform(post(ADMIN_SPACES_PATH)
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.name").value("보안 테스트 공간"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number createdSpaceId = com.jayway.jsonpath.JsonPath.read(createResponse, "$.data.id");
        long spaceId = createdSpaceId.longValue();
        mockMvc.perform(patch(ADMIN_SPACES_PATH + "/{spaceId}", spaceId)
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"수정된 보안 테스트 공간","openingTime":"09:30"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("수정된 보안 테스트 공간"));

        assertThat(auditLogRepository.findAll())
                .filteredOn(auditLog -> auditLog.getActorMemberId().equals(admin.getId()))
                .extracting(auditLog -> auditLog.getAction())
                .containsExactlyInAnyOrder(AuditAction.REGISTER_SPACE, AuditAction.MODIFY_SPACE);
    }

    private Member saveMember(MemberRole role, MemberStatus status) {
        Member member = new Member(
                role.name().toLowerCase() + "-" + status.name().toLowerCase() + "-" + System.nanoTime() + "@test.com",
                "encoded-password",
                "security-test-member");
        ReflectionTestUtils.setField(member, "role", role);
        ReflectionTestUtils.setField(member, "status", status);
        return memberRepository.saveAndFlush(member);
    }

    private String bearerToken(Member member) {
        return "Bearer " + jwtProvider.genAccessToken(member);
    }

    private String validCreateRequest() {
        return """
                {
                  "name": "보안 테스트 공간",
                  "location": "서울시 강남구",
                  "description": "관리자 보안 통합 테스트",
                  "capacity": 8,
                  "pricePerSlot": 5000,
                  "openingTime": "09:00",
                  "closingTime": "18:00"
                }
                """;
    }
}
