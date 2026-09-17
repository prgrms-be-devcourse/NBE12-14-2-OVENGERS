package com.ovengers.slotkey.auth.service;

import com.ovengers.slotkey.auth.entity.RefreshToken;
import com.ovengers.slotkey.auth.repository.RefreshTokenRepository;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.repository.MemberRepository;
import com.ovengers.slotkey.support.MySqlTestContainerConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(MySqlTestContainerConfig.class)
@Transactional
public class RefreshTokenServiceTest {

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("리프레시 토큰의 DB 만료 시각은 발급 시점부터 약 24시간이다")
    void t1() throws Exception {
        // 1. 회원 준비
        Member member = memberRepository.save(
                new Member(
                        "refresh-lifetime@example.com",
                        passwordEncoder.encode("Test1234!"),
                        "테스트회원"
                )
        );

        // 2. 발급 직전과 직후의 시각 기록
        LocalDateTime beforeIssue = LocalDateTime.now();

        String rawToken =
                refreshTokenService.issueRefreshToken(member);

        LocalDateTime afterIssue = LocalDateTime.now();

        assertThat(rawToken).isNotBlank();

        // 3. 반환된 원문을 해시해서 해당 DB 기록을 찾는다.
        String tokenHash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                        .digest(rawToken.getBytes(StandardCharsets.UTF_8))
        );

        // 메모리에 있는 객체가 아닌 DB 저장 값을 확인
        entityManager.flush();
        entityManager.clear();

        RefreshToken savedToken = refreshTokenRepository
                .findByTokenHash(tokenHash)
                .orElseThrow();

        // 4. 발급 시점 + 24시간 범위에 있는지 확인
        // DB 시간 정밀도 차이를 고려해 1초의 여유를 둔다.
        assertThat(savedToken.getExpiresAt()).isBetween(
                beforeIssue.plusHours(24).minusSeconds(1),
                afterIssue.plusHours(24).plusSeconds(1)
        );
    }
}