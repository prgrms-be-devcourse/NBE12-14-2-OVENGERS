package com.ovengers.slotkey.auth.entity;

import com.ovengers.slotkey.member.entity.Member;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_token")
@Getter
@NoArgsConstructor
public class RefreshToken {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        // 이 리프레시 토큰을 발급받은 회원 optional은 회원 연결이 필수라는 의미
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "member_id", nullable = false)
        private Member member;

        // 리프레시 토큰 원문을 해시한 값
        @Column(name = "token_hash", nullable = false, unique = true, length = 64)
        private String tokenHash;

        // 이 토큰으로 갱신할 수 있는 마지막 시각
        @Column(name = "expires_at", nullable = false)
        private LocalDateTime expiresAt;

        // 폐기 시각: null이면 아직 폐기하지 않은 토큰
        @Column(name = "revoked_at")
        private LocalDateTime revokedAt;

        // 폐기 여부 확인
        public boolean isRevoked() {
        return revokedAt != null;
        }

        // 토큰 폐기
        public void revoke(LocalDateTime now) {
        if (revokedAt == null) {
            revokedAt = now;
            }
        }

        public RefreshToken(
                Member member,
                String tokenHash,
                LocalDateTime expiresAt
        ) {
            this.member = member;
            this.tokenHash = tokenHash;
            this.expiresAt = expiresAt;
        }

        // 만료 시각과 같거나 지났으면 만료
        public boolean isExpired(LocalDateTime now) {
            return !now.isBefore(expiresAt);
        }
}