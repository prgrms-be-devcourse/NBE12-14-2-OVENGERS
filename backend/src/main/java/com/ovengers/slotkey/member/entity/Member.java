package com.ovengers.slotkey.member.entity;

import com.ovengers.slotkey.global.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;
import java.time.LocalDateTime;

@Entity
@DynamicUpdate
@Table(name = "member")
@Getter
@NoArgsConstructor
public class Member extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 254)
    private String email;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberStatus status;

    @Column(nullable = false)
    private int balance = 0;

    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    public Member(String email, String nickname) {
        this.email = email;
        this.nickname = nickname;
        this.role = MemberRole.USER;
        this.status = MemberStatus.ACTIVE;
    }

    public void updateStatus(MemberStatus status) {
        this.status = status;
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    public void withdraw(LocalDateTime withdrawnAt) {
        this.status = MemberStatus.WITHDRAWN;
        this.withdrawnAt = withdrawnAt;
    }

    public int expireRemainingCredit() {
        int expiredAmount = this.balance;
        this.balance = 0;
        return expiredAmount;
    }

    public void addCredits(int amount) {
        this.balance += amount;
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    public void verifyEmail(LocalDateTime verifiedAt) {
        if (verifiedAt == null) {
            throw new IllegalArgumentException("인증 시각은 필수입니다.");
        }

        if (this.emailVerifiedAt == null) {
            this.emailVerifiedAt = verifiedAt;
        }
    }
}
