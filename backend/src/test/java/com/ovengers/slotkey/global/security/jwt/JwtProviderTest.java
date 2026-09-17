package com.ovengers.slotkey.global.security.jwt;

import com.ovengers.slotkey.member.entity.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtProviderTest {

    private static final String TEST_SECRET_KEY =
            "test-secret-key-for-jwt-provider-test-0123456789abcdef";

    @Test
    @DisplayName("액세스 토큰의 발급 시각과 만료 시각 차이는 300초다")
    void t1() {
        // 1. Spring Context 없이 JwtProvider를 직접 생성
        JwtProvider jwtProvider = new JwtProvider();

        ReflectionTestUtils.setField(
                jwtProvider,
                "secretKey",
                TEST_SECRET_KEY
        );
        ReflectionTestUtils.setField(
                jwtProvider,
                "expireMillis",
                300_000L
        );

        // 2. DB 저장 없이 토큰에 넣을 회원 정보만 준비
        Member member = mock(Member.class);

        when(member.getId()).thenReturn(1L);
        when(member.getEmail())
                .thenReturn("access-lifetime@example.com");

        // 3. 액세스 토큰 발급
        String accessToken = jwtProvider.genAccessToken(member);

        // 4. 토큰의 발급·만료 시각 확인
        Map<String, Object> payload =
                jwtProvider.payloadOrNull(accessToken);

        assertThat(payload).isNotNull();

        long issuedAt = ((Number) payload.get("iat")).longValue();
        long expiresAt = ((Number) payload.get("exp")).longValue();

        assertThat(expiresAt - issuedAt).isEqualTo(300L);
    }
}