package com.ovengers.slotkey.auth.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "verification_grant",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_verification_grant_challenge",
                        columnNames = "challenge_id"
                ),
                @UniqueConstraint(
                        name = "uk_verification_grant_hash",
                        columnNames = "grant_hash"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VerificationGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "challenge_id", nullable = false)
    private VerificationChallenge challenge;

    @Column(name = "grant_hash", nullable = false, length = 64)
    private String grantHash;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    @Column(name = "invalidated_at")
    private LocalDateTime invalidatedAt;

    public VerificationGrant(
            VerificationChallenge challenge,
            String grantHash,
            LocalDateTime issuedAt
    ) {
        if (challenge.getVerifiedAt() == null) {
            throw new IllegalArgumentException(
                    "인증 완료된 챌린지가 필요합니다."
            );
        }

        this.challenge = challenge;
        this.grantHash = grantHash;
        this.issuedAt = issuedAt;
        this.expiresAt = issuedAt.plusMinutes(5);
    }

    public boolean isUsable(LocalDateTime now) {
        return consumedAt == null
                && invalidatedAt == null
                && now.isBefore(expiresAt);
    }

    public void consume(LocalDateTime now) {
        if (!isUsable(now)) {
            throw new IllegalStateException(
                    "사용할 수 없는 인증 증표입니다."
            );
        }

        this.consumedAt = now;
    }

    public void invalidate(LocalDateTime now) {
        if (invalidatedAt == null) {
            this.invalidatedAt = now;
        }
    }
}