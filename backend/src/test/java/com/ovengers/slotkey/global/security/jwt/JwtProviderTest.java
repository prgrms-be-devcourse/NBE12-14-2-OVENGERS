package com.ovengers.slotkey.global.security.jwt;

import com.ovengers.slotkey.member.entity.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.ovengers.slotkey.member.entity.MemberRole;

import java.util.Map;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtProviderTest {

    private static final String TEST_SECRET_KEY =
            "test-secret-key-for-jwt-provider-test-0123456789abcdef";

    @Test
    @DisplayName("세션 연결 AT는 UTC와 서울 시간대 모두 5분을 넘지 않고 세션 종료도 넘지 않는다")
    void sessionBoundTokenUsesClockAndCapsExpirationAcrossZones() {
        for (ZoneId zone : new ZoneId[]{ZoneId.of("UTC"), ZoneId.of("Asia/Seoul")}) {
        // 1. Spring Context 없이 JwtProvider를 직접 생성
        Clock clock = Clock.fixed(Instant.parse("2026-10-19T00:00:00Z"), zone);
        JwtProvider jwtProvider = new JwtProvider(clock);

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
        when(member.getRole()).thenReturn(MemberRole.USER);
        // 3. 액세스 토큰 발급
        LocalDateTime sessionExpiresAt = LocalDateTime.now(clock).plusHours(24);
        String accessToken = jwtProvider.genAccessToken(
                member, "test-session", sessionExpiresAt
        );

        // 4. 토큰의 발급·만료 시각 확인
        Map<String, Object> payload =
                jwtProvider.payloadOrNull(accessToken);

        assertThat(payload).isNotNull();

        long issuedAt = ((Number) payload.get("iat")).longValue();
        long expiresAt = ((Number) payload.get("exp")).longValue();

        assertThat(expiresAt - issuedAt).isEqualTo(300L);
        assertThat(Instant.ofEpochSecond(expiresAt))
                .isBeforeOrEqualTo(sessionExpiresAt.atZone(zone).toInstant());

        // 세션이 90초 남은 시점의 AT는 90초를 넘지 않는다.
        Clock nearExpiryClock = Clock.fixed(
                sessionExpiresAt.minusSeconds(90).atZone(zone).toInstant(), zone
        );
        JwtProvider nearExpiryProvider = new JwtProvider(nearExpiryClock);
        ReflectionTestUtils.setField(nearExpiryProvider, "secretKey", TEST_SECRET_KEY);
        ReflectionTestUtils.setField(nearExpiryProvider, "expireMillis", 300_000L);
        String cappedToken = nearExpiryProvider.genAccessToken(
                member, "test-session", sessionExpiresAt
        );
        Map<String, Object> cappedPayload = jwtProvider.payloadOrNull(cappedToken);
        long cappedIat = ((Number) cappedPayload.get("iat")).longValue();
        long cappedExp = ((Number) cappedPayload.get("exp")).longValue();
        assertThat(cappedExp - cappedIat).isEqualTo(90L);
        assertThat(Instant.ofEpochSecond(cappedExp))
                .isEqualTo(sessionExpiresAt.atZone(zone).toInstant());
        }

        String actualJvmZone = ZoneId.systemDefault().getId();
        System.out.println("AUTH_TEST_JVM_ZONE_ID=" + actualJvmZone);
        String expectedJvmZone = System.getProperty("slotkey.test.expected-zone");
        if (expectedJvmZone != null) {
            assertThat(actualJvmZone).isEqualTo(expectedJvmZone);
        }
    }
}
