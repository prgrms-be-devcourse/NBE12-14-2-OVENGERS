package com.ovengers.slotkey.access.support;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

@Component
public class AccessTokenHasher {

    // 출입 토큰 해시할 때 SHA-256 알고리즘 사용
    private static final String HASH_ALGORITHM = "SHA-256";

    public String hash(String rawToken) {
        Objects.requireNonNull(rawToken, "원문 토큰은 null일 수 없습니다.");

        try {
            // SHA-256 알고리즘의 해시 계산 객체 생성
            MessageDigest messageDigest = MessageDigest.getInstance(HASH_ALGORITHM);

            // 원문 토큰을 바이트로 변환한 다음 해시 계산
            byte[] hashBytes = messageDigest.digest(rawToken.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hashBytes);

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", exception);
        }
    }
}
