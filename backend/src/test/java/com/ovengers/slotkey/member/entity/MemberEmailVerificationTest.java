package com.ovengers.slotkey.member.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

class MemberEmailVerificationTest {

    @Test
    void 신규_회원은_미인증이다() {
        Member member = new Member("user@example.com", "회원");

        assertThat(member.isEmailVerified()).isFalse();
        assertThat(member.getEmailVerifiedAt()).isNull();
    }

    @Test
    void 인증하면_인증_시각이_기록된다() {
        Member member = new Member("user@example.com", "회원");
        LocalDateTime verifiedAt =
                LocalDateTime.of(2026, 10, 13, 12, 0);

        member.verifyEmail(verifiedAt);

        assertThat(member.isEmailVerified()).isTrue();
        assertThat(member.getEmailVerifiedAt()).isEqualTo(verifiedAt);
    }

    @Test
    void 재호출해도_최초_인증_시각을_유지한다() {
        Member member = new Member("user@example.com", "회원");
        LocalDateTime first =
                LocalDateTime.of(2026, 10, 13, 12, 0);

        member.verifyEmail(first);
        member.verifyEmail(first.plusMinutes(5));

        assertThat(member.getEmailVerifiedAt()).isEqualTo(first);
    }

    @Test
    void 인증_시각이_없으면_거부하고_미인증을_유지한다() {
        Member member = new Member("user@example.com", "회원");

        assertThatThrownBy(() -> member.verifyEmail(null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(member.isEmailVerified()).isFalse();
    }
}