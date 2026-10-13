package com.ovengers.slotkey.auth.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "verification_challenge")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VerificationChallenge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guard_id", nullable = false)
    private VerificationGuard guard;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "invalidated_at")
    private LocalDateTime invalidatedAt;

    public VerificationChallenge(
            VerificationGuard guard,
            String codeHash,
            LocalDateTime issuedAt
    ) {
        this.guard = guard;
        this.codeHash = codeHash;
        this.issuedAt = issuedAt;
        this.expiresAt = issuedAt.plusMinutes(3);
    }

    public boolean isUsable(LocalDateTime now) {
        return verifiedAt == null
                && invalidatedAt == null
                && now.isBefore(expiresAt);
    }

    public void markVerified(LocalDateTime now) {
        if (!isUsable(now)) {
            throw new IllegalStateException(
                    "사용할 수 없는 인증번호입니다."
            );
        }

        this.verifiedAt = now;
    }

    public void invalidate(LocalDateTime now) {
        if (invalidatedAt == null) {
            this.invalidatedAt = now;
        }
    }
}