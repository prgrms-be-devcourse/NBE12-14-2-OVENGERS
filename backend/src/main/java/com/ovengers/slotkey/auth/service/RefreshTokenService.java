package com.ovengers.slotkey.auth.service;

import com.ovengers.slotkey.auth.entity.RefreshToken;
import com.ovengers.slotkey.auth.entity.AuthSession;
import com.ovengers.slotkey.auth.dto.internal.IssuedRefreshToken;
import com.ovengers.slotkey.auth.repository.RefreshTokenRepository;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.entity.MemberStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.Clock;
import java.util.HexFormat;
import java.util.UUID;
import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final EntityManager entityManager;
    private final AuthSessionService authSessionService;
    private final Clock clock;
    // 리프레시 토큰 발급
    @Transactional
    public IssuedRefreshToken issueRefreshToken(Member member) {
        AuthSession authSession = authSessionService.create(member);
        // 1. 사용자에게 전달할 UUID 원문 생성
        String rawToken = generateRefreshToken();

        // 2. DB에 저장할 해시 생성
        String tokenHash = hashRefreshToken(rawToken);

        // 3. 만료 시각 설정: 발급 시점부터 1일
        LocalDateTime expiresAt = authSession.getExpiresAt();

        // 4. 회원·해시·만료 시각으로 엔티티 생성
        RefreshToken refreshToken = new RefreshToken(
                member,
                authSession,
                tokenHash,
                expiresAt
        );

        // 5. DB에 저장
        refreshTokenRepository.save(refreshToken);

        // 6. 쿠키로 전달할 수 있도록 원문 반환
        return new IssuedRefreshToken(
                rawToken,
                authSession.getId(),
                authSession.getExpiresAt()
        );
    }
    // 사용자에게 전달할 리프레시 토큰 원문 생성
    private String generateRefreshToken() {
        return UUID.randomUUID().toString();
    }


    // 발급 후 DB에 저장할 리프레시 토큰 해시 생성
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

    // 리프레시를 검증하고 해당 회원을 반환
    @Transactional
    public RefreshValidation validateRefreshToken(String rawToken) {
        // 1. 쿠키에 토큰이 있는지 확인
        if (rawToken == null || rawToken.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        // 2. 받은 원문을 해시해서 DB 기록 조회
        String tokenHash = hashRefreshToken(rawToken);

        RefreshToken refreshToken = refreshTokenRepository
                .findWithSessionByTokenHash(tokenHash)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN)
                );

        // 3. 만료·폐기 여부 확인
        LocalDateTime now = LocalDateTime.now(clock);

        if (refreshToken.isExpired(now) || refreshToken.isRevoked()
                || !refreshToken.getAuthSession().isActiveAt(now)) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        // 4. 현재 계정 상태 확인
        Member member = refreshToken.getMember();

        entityManager.refresh(member, LockModeType.PESSIMISTIC_WRITE);

        if (member.getStatus() == MemberStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.ACCOUNT_WITHDRAWN);
        }

        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.ACCOUNT_INACTIVE);
        }

        return new RefreshValidation(member, refreshToken.getAuthSession());
    }

    // 리프레시 토큰 폐기 revoke_at의 값이 null이 아니면 폐기
    @Transactional
    public void revokeRefreshToken(String rawToken) {
        // 쿠키가 없으면 처리할 토큰도 없으므로 종료
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }

        String tokenHash = hashRefreshToken(rawToken);

        refreshTokenRepository.findWithSessionByTokenHash(tokenHash)
                .ifPresent(refreshToken -> {
                    LocalDateTime now = LocalDateTime.now(clock);
                    authSessionService.revoke(refreshToken.getAuthSession(), now);
                    refreshTokenRepository.revokeAllBySessionId(
                            refreshToken.getAuthSession().getId(), now
                    );
                });
    }

    public record RefreshValidation(Member member, AuthSession authSession) {}
}
