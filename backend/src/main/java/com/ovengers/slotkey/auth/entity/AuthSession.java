package com.ovengers.slotkey.auth.entity;

import com.ovengers.slotkey.member.entity.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "auth_session")
@Getter
@NoArgsConstructor
public class AuthSession {

    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    public AuthSession(Member member, LocalDateTime now) {
        this.id = UUID.randomUUID().toString();
        this.member = member;
        this.createdAt = now;
        this.expiresAt = now.plusHours(24);
    }

    public boolean isActiveAt(LocalDateTime now) {
        return revokedAt == null && now.isBefore(expiresAt);
    }

    public void revoke(LocalDateTime now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }
}
