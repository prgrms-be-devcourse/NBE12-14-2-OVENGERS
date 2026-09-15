package com.ovengers.slotkey.auth.service;

import com.ovengers.slotkey.auth.entity.RefreshToken;
import com.ovengers.slotkey.auth.repository.RefreshTokenRepository;
import com.ovengers.slotkey.member.entity.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public String issueRefreshToken(Member member) {
        // 1. 사용자에게 전달할 UUID 원문 생성
        String rawToken = generateRefreshToken();

        // 2. DB에 저장할 해시 생성
        String tokenHash = hashRefreshToken(rawToken);

        // 3. 만료 시각 설정: 발급 시점부터 7일
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(7);

        // 4. 회원·해시·만료 시각으로 엔티티 생성
        RefreshToken refreshToken = new RefreshToken(
                member,
                tokenHash,
                expiresAt
        );

        // 5. DB에 저장
        refreshTokenRepository.save(refreshToken);

        // 6. 쿠키로 전달할 수 있도록 원문 반환
        return rawToken;
    }
    // 사용자에게 전달할 리프레시 토큰 원문 생성
    private String generateRefreshToken() {
        return UUID.randomUUID().toString();
    }


    // DB에 저장할 리프레시 토큰 해시 생성
    private String hashRefreshToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hashBytes = digest.digest(
                    rawToken.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }
}
