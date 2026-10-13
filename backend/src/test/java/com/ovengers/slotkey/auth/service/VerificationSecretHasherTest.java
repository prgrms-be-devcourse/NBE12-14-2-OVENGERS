package com.ovengers.slotkey.auth.service;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

class VerificationSecretHasherTest {

    private final VerificationSecretHasher hasher =
            new VerificationSecretHasher(
                    Base64.getEncoder().encodeToString(new byte[32])
            );

    @Test
    void 정확한_번호만_일치한다() {
        String hash = hasher.hashCode(1L, "01234567");

        assertThat(hash).hasSize(64);
        assertThat(hasher.matchesCode(1L, "01234567", hash)).isTrue();
        assertThat(hasher.matchesCode(1L, "01234568", hash)).isFalse();
    }

    @Test
    void 다른_Guard에서는_같은_번호도_일치하지_않는다() {
        String hash = hasher.hashCode(1L, "01234567");

        assertThat(hasher.matchesCode(2L, "01234567", hash)).isFalse();
    }

    @Test
    void 번호와_증표는_같은_원문이라도_검증값이_다르다() {
        String raw = "01234567";

        assertThat(hasher.hashGrant(raw))
                .isNotEqualTo(hasher.hashCode(1L, raw));
    }

    @Test
    void 다른_비밀키로는_번호가_일치하지_않는다() {
        byte[] anotherKey = new byte[32];
        anotherKey[0] = 1;

        VerificationSecretHasher anotherHasher =
                new VerificationSecretHasher(
                        Base64.getEncoder().encodeToString(anotherKey)
                );

        String hash = hasher.hashCode(1L, "01234567");

        assertThat(anotherHasher.matchesCode(
                1L, "01234567", hash
        )).isFalse();
    }

    @Test
    void 짧은_비밀키는_거부한다() {
        String shortKey =
                Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() ->
                new VerificationSecretHasher(shortKey)
        ).isInstanceOf(IllegalArgumentException.class);
    }
}