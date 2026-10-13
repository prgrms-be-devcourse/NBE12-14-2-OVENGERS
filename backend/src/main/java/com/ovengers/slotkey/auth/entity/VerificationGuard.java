package com.ovengers.slotkey.auth.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "verification_guard",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_verification_guard_email_purpose",
                columnNames = {"email", "purpose"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VerificationGuard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 254)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private VerificationPurpose purpose;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "block_stage", nullable = false)
    private int blockStage;

    @Column(name = "blocked_until")
    private LocalDateTime blockedUntil;

    @Column(name = "admin_locked", nullable = false)
    private boolean adminLocked;

    @Column(name = "last_sent_at")
    private LocalDateTime lastSentAt;

    public VerificationGuard(
            String email,
            VerificationPurpose purpose
    ) {
        this.email = email;
        this.purpose = purpose;
    }
    public boolean isBlocked(LocalDateTime now) {
        return adminLocked
                || (blockedUntil != null && now.isBefore(blockedUntil));
    }

    public void prepareAttempt(LocalDateTime now) {
        if (isBlocked(now)) {
            throw new IllegalStateException("이메일 인증이 차단된 상태입니다.");
        }

        // 일시 차단이 끝나면 현재 단계의 횟수만 초기화
        if (blockedUntil != null) {
            failedAttempts = 0;
            blockedUntil = null;
        }
    }

    // 반환값: 이번 오입력으로 차단이 시작됐는가?
    public boolean recordFailure(LocalDateTime now) {
        prepareAttempt(now);

        failedAttempts++;

        if (failedAttempts < 5) {
            return false;
        }

        blockStage++;

        switch (blockStage) {
            case 1 -> blockedUntil = now.plusMinutes(5);
            case 2 -> blockedUntil = now.plusHours(1);
            default -> {
                adminLocked = true;
                blockedUntil = null;
            }
        }

        return true;
    }

    public void recordSuccess(LocalDateTime now) {
        prepareAttempt(now);
        resetFailureState();
    }

    public void unlockByAdmin() {
        resetFailureState();
    }

    private void resetFailureState() {
        failedAttempts = 0;
        blockStage = 0;
        blockedUntil = null;
        adminLocked = false;
    }

    public boolean canSend(LocalDateTime now) {
        if (isBlocked(now)) {
            return false;
        }

        return lastSentAt == null
                || !now.isBefore(lastSentAt.plusSeconds(30));
    }

    public void recordSent(LocalDateTime now) {
        if (!canSend(now)) {
            throw new IllegalStateException(
                    "인증번호 발송이 차단됐거나 재발송 간격이 지나지 않았습니다."
            );
        }

        prepareAttempt(now);
        lastSentAt = now;
    }
}