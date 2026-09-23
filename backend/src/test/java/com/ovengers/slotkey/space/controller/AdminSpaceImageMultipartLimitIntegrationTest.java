package com.ovengers.slotkey.space.controller;

import com.ovengers.slotkey.global.security.jwt.JwtProvider;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.entity.MemberRole;
import com.ovengers.slotkey.member.entity.MemberStatus;
import com.ovengers.slotkey.member.repository.MemberRepository;
import com.ovengers.slotkey.space.entity.Space;
import com.ovengers.slotkey.space.entity.SpaceStatus;
import com.ovengers.slotkey.space.repository.SpaceRepository;
import com.ovengers.slotkey.support.MySqlTestContainerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(MySqlTestContainerConfig.class)
class AdminSpaceImageMultipartLimitIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private SpaceRepository spaceRepository;

    @Autowired
    private JwtProvider jwtProvider;

    @Test
    @DisplayName("5MB 초과 multipart 요청 시 실제 서블릿 multipart 파서가 MaxUploadSizeExceededException을 발생시키고 413 IMAGE_SIZE_EXCEEDED를 반환한다")
    void uploadSpaceImage_exceedsServletMultipartLimit_returns413() {
        Member admin = saveMember(MemberRole.ADMIN, MemberStatus.ACTIVE);
        Space space = saveSpace(SpaceStatus.ACTIVE);

        // 5MB + 10KB 크기의 페이로드 (서블릿 max-file-size 5MB 초과)
        int payloadSize = (5 * 1024 * 1024) + (10 * 1024);
        byte[] oversizedBytes = new byte[payloadSize];
        oversizedBytes[0] = (byte) 0xFF;
        oversizedBytes[1] = (byte) 0xD8;
        oversizedBytes[2] = (byte) 0xFF;

        ByteArrayResource resource = new ByteArrayResource(oversizedBytes) {
            @Override
            public String getFilename() {
                return "oversized.jpg";
            }
        };

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.genAccessToken(admin));

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", resource);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/admin/spaces/{spaceId}/image",
                HttpMethod.PUT,
                requestEntity,
                String.class,
                space.getId()
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).contains("\"status\":\"FAIL\"");
        assertThat(response.getBody()).contains("\"code\":\"IMAGE_SIZE_EXCEEDED\"");
    }

    private Member saveMember(MemberRole role, MemberStatus status) {
        Member member = new Member(
                role.name().toLowerCase() + "-" + status.name().toLowerCase() + "-" + System.nanoTime() + "@test.com",
                "encoded-password",
                "limit-test-member");
        ReflectionTestUtils.setField(member, "role", role);
        ReflectionTestUtils.setField(member, "status", status);
        return memberRepository.saveAndFlush(member);
    }

    private Space saveSpace(SpaceStatus status) {
        return spaceRepository.saveAndFlush(Space.builder()
                .name("용량 제한 테스트 공간")
                .location("서울시 강남구")
                .description("용량 제한 통합 테스트")
                .capacity(8)
                .pricePerSlot(5000L)
                .openingTime(LocalTime.of(9, 0))
                .closingTime(LocalTime.of(18, 0))
                .status(status)
                .version(1)
                .build());
    }
}
