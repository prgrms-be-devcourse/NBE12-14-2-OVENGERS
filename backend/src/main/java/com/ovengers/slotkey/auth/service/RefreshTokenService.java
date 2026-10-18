package com.ovengers.slotkey.auth.service;

import com.ovengers.slotkey.auth.dto.internal.IssuedRefreshToken;
import com.ovengers.slotkey.auth.entity.AuthSession;
import com.ovengers.slotkey.auth.entity.RefreshToken;
import com.ovengers.slotkey.auth.repository.AuthSessionRepository;
import com.ovengers.slotkey.auth.repository.RefreshTokenRepository;
import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.entity.MemberStatus;
import com.ovengers.slotkey.member.repository.MemberRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthSessionRepository authSessionRepository;
    private final MemberRepository memberRepository;
    private final EntityManager entityManager;
    private final Clock clock;

    @Transactional
    public IssuedRefreshToken issueRefreshToken(Member member) {
        AuthSession authSession = authSessionRepository.save(
                new AuthSession(member, LocalDateTime.now(clock))
        );
        String rawToken = generateRefreshToken();
        refreshTokenRepository.save(new RefreshToken(
                member,
                authSession,
                hashRefreshToken(rawToken),
                authSession.getExpiresAt()
        ));

        return new IssuedRefreshToken(
                rawToken,
                authSession.getId(),
                authSession.getExpiresAt()
        );
    }

    /**
     * Locks in the global order member -> session -> token. A replay returns a
     * value instead of throwing so the revocation transaction can commit before
     * the caller converts it to an HTTP 401.
     */
    @Transactional
    public RotationResult rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        String tokenHash = hashRefreshToken(rawToken);
        RefreshTokenRepository.RefreshTokenReference reference = refreshTokenRepository
                .findReferenceByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

        Member member = memberRepository.findByIdForUpdate(reference.getMemberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
        AuthSession session = authSessionRepository.findByIdForUpdate(reference.getSessionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
        RefreshToken refreshToken = refreshTokenRepository.findByIdForUpdate(reference.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

        LocalDateTime now = LocalDateTime.now(clock);
        if (!member.getId().equals(refreshToken.getMember().getId())
                || !session.getId().equals(refreshToken.getAuthSession().getId())) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        if (refreshToken.isConsumed() && session.isActiveAt(now)
                && !refreshToken.isExpired(now)) {
            session.revoke(now);
            refreshTokenRepository.revokeAllBySessionId(session.getId(), now);
            return RotationResult.reusedResult();
        }

        if (refreshToken.isExpired(now) || refreshToken.isRevoked()
                || !session.isActiveAt(now)) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        if (member.getStatus() == MemberStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.ACCOUNT_WITHDRAWN);
        }
        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.ACCOUNT_INACTIVE);
        }

        refreshToken.consume(now);
        String replacement = generateRefreshToken();
        refreshTokenRepository.saveAndFlush(new RefreshToken(
                member,
                session,
                hashRefreshToken(replacement),
                session.getExpiresAt()
        ));

        return RotationResult.rotated(replacement, member, session);
    }

    @Transactional
    public void revokeRefreshToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }

        String tokenHash = hashRefreshToken(rawToken);
        refreshTokenRepository.findWithSessionByTokenHash(tokenHash)
                .ifPresent(refreshToken -> {
                    LocalDateTime now = LocalDateTime.now(clock);
                    AuthSession session = refreshToken.getAuthSession();
                    entityManager.refresh(session, LockModeType.PESSIMISTIC_WRITE);
                    session.revoke(now);
                    refreshTokenRepository.revokeAllBySessionId(session.getId(), now);
                });
    }

    private String generateRefreshToken() {
        return UUID.randomUUID().toString();
    }

    private String hashRefreshToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(rawToken.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }

    public record RotationResult(
            boolean reused,
            String rawToken,
            Member member,
            AuthSession session
    ) {
        private static RotationResult reusedResult() {
            return new RotationResult(true, null, null, null);
        }

        private static RotationResult rotated(
                String rawToken,
                Member member,
                AuthSession session
        ) {
            return new RotationResult(false, rawToken, member, session);
        }
    }
}
