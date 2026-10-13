package com.ovengers.slotkey.auth.entity;

import com.ovengers.slotkey.member.entity.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "local_credential")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LocalCredential {

    @Id
    @Column(name = "member_id")
    private Long memberId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "password_changed_at", nullable = false)
    private LocalDateTime passwordChangedAt;

    public LocalCredential(
            Member member,
            String passwordHash,
            LocalDateTime passwordChangedAt
    ) {
        this.member = member;
        this.passwordHash = passwordHash;
        this.passwordChangedAt = passwordChangedAt;
    }
}